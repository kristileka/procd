package tech.procd.customer.account.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.impl.JWTParser
import com.auth0.jwt.interfaces.DecodedJWT
import tech.procd.customer.Customer
import tech.procd.customer.CustomerProvider
import tech.procd.customer.account.Account
import tech.procd.customer.account.AccountStore
import tech.procd.keycloak.KeycloakUserAccount
import io.ktor.http.auth.*
import io.ktor.server.auth.jwt.*
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Suppress("TooManyFunctions")
class TokenExtractor(
    private val accountStore: AccountStore
) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Suppress("ReturnCount")
    suspend fun extractAccount(authorizationHeader: HttpAuthHeader?): Account? {
        try {
            val tokenDetails = extractTokenDetails(authorizationHeader) ?: return null
            val account = accountStore.load(tokenDetails.accountId) ?: return null

            return account.copy(
                assignedRoles = tokenDetails.accountRoles,
                assignedGroups = tokenDetails.accountGroup
                    .map {
                        CustomerProvider.externalGroupRelation[it]
                            ?: error("Unable to map keycloak group `$it`")
                    },
            )
        } catch (cause: Exception) {
            logger.error(cause.stackTraceToString())

            return null
        }
    }

    fun extractTokenDetails(authorizationHeader: HttpAuthHeader?): TokenData? {
        if (authorizationHeader !is HttpAuthHeader.Single) {
            return null
        }

        return extractTokenDetails(authorizationHeader.blob)
    }

    fun extractTokenDetails(token: String): TokenData? {
        try {
            if (token.isEmpty()) {
                return null
            }

            val credentials = token.asJWTCredential()
            val accountId = credentials.accountId()

            return TokenData(
                accountId = credentials.accountId(),
                customerId = Customer.Id.create(accountId, credentials.realm()),
                accountRoles = credentials.roles(),
                accountGroup = credentials.groups(),
                tokenId = credentials.tokenId(),
                sessionId = credentials.sessionId(),
                issuedAt = credentials.issuedAt(),
                expireAt = credentials.expireAt(),
            )
        } catch (cause: Exception) {
            logger.error(cause.stackTraceToString())

            return null
        }
    }

    private fun String.asJWTCredential(): JWTCredential = try {
        val jwt = JWT.decode(this)
        val payload = jwt.parsePayload()

        JWTCredential(payload)
    } catch (cause: Exception) {
        logger.error(cause.stackTraceToString())

        throw AuthException.IncorrectAuthToken
    }

    private fun DecodedJWT.parsePayload() = JWTParser()
        .parsePayload(
            String(
                Base64.getUrlDecoder().decode(payload),
            ),
        )

    private fun JWTCredential.realm() = Account.Realm(
        payload.getClaim("iss").asString().split("/").last(),
    )

    private fun JWTCredential.accountId() = Account.Id(
        UUID.fromString(payload.getClaim("sub").asString()),
    )

    private fun JWTCredential.tokenId() = UUID.nameUUIDFromBytes(
        payload.getClaim("jti").asString().toByteArray(),
    )

    private fun JWTCredential.sessionId() = UUID.nameUUIDFromBytes(
        payload.getClaim("iss").asString().toByteArray(),
    )

    private fun JWTCredential.issuedAt() = LocalDateTime.ofInstant(
        Instant.ofEpochSecond(payload.getClaim("iat").asLong()),
        ZoneId.systemDefault(),
    )

    private fun JWTCredential.expireAt() = LocalDateTime.ofInstant(
        Instant.ofEpochSecond(payload.getClaim("exp").asLong()),
        ZoneId.systemDefault(),
    )

    private fun JWTCredential.roles(): List<String> {
        val assignedRoles = payload.getClaim("realm_access")
            ?.asMap()
            ?.getOrDefault("roles", listOf<String>())
            ?: listOf<String>()

        @Suppress("UNCHECKED_CAST")
        return assignedRoles as List<String>
    }

    private fun JWTCredential.groups(): List<String> {
        val groups = payload
            .getClaim("groups")
            ?.asList(KeycloakUserAccount.Group::class.java)

        return groups?.map { it.toApplicationRepresentation() } ?: listOf()
    }
}

data class TokenData(
    val accountId: Account.Id,
    val customerId: Customer.Id,
    val accountRoles: List<String>,
    val accountGroup: List<String>,
    val tokenId: UUID,
    val sessionId: UUID,
    val issuedAt: LocalDateTime,
    val expireAt: LocalDateTime
)
