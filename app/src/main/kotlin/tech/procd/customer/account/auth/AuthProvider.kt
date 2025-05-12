package tech.procd.customer.account.auth

import tech.procd.customer.CustomerProvider
import tech.procd.customer.account.AccountStore
import tech.procd.common.CommonApiException
import tech.procd.keycloak.admin.KeycloakAdminApiClient
import tech.procd.keycloak.api.KeycloakClient
import io.ktor.http.auth.*
import java.time.LocalDateTime

class AuthProvider(
    private val keycloakClient: KeycloakClient,
    private val keycloakAdminClient: KeycloakAdminApiClient,
    private val tokenExtractor: TokenExtractor,
    private val accountStore: AccountStore,
) {
    suspend fun token(
        email: String,
        password: String,
    ): AccessToken =
        try {
            val account = accountStore.find(email)
                ?: throw CommonApiException.IncorrectRequestParameters.create(
                    "server",
                    "Email or password incorrect",
                )


            val issuedToken = keycloakClient.token(
                username = email,
                password = password,
            )

            val tokenDetails = tokenExtractor.extractTokenDetails(issuedToken.accessToken)!!

            accountStore.store(
                account.copy(
                    assignedGroups = tokenDetails.accountGroup.map {
                        CustomerProvider.externalGroupRelation[it]
                            ?: error("Unable to map keycloak group `$it`")
                    },
                    lastAuthorizedAt = LocalDateTime.now(),
                ),
            )
            AccessToken(
                accessToken = issuedToken.accessToken,
                expiresIn = issuedToken.expiresIn,
                type = issuedToken.tokenType,
                refreshToken = issuedToken.refreshToken,
                refreshExpiresIn = issuedToken.refreshExpiresIn,
            )
        } catch (cause: Exception) {
            throw cause
        }

    suspend fun refresh(token: String): AccessToken = try {
        val refreshedToken = keycloakClient.refresh(token)

        AccessToken(
            accessToken = refreshedToken.accessToken,
            expiresIn = refreshedToken.expiresIn,
            type = refreshedToken.tokenType,
            refreshToken = refreshedToken.refreshToken,
            refreshExpiresIn = refreshedToken.refreshExpiresIn,
        )
    } catch (cause: Exception) {
        throw AuthException.Failed(cause.message ?: "")
    }

    suspend fun logout(authorizationHeader: HttpAuthHeader?) {
        if (authorizationHeader !is HttpAuthHeader.Single) {
            return
        }

        logout(authorizationHeader.blob)
    }

    private suspend fun logout(token: String?) {
        if (token.isNullOrEmpty()) {
            return
        }

        try {
            val tokenDetails = tokenExtractor.extractTokenDetails(token) ?: return
            val account = accountStore.load(tokenDetails.accountId) ?: return

            keycloakAdminClient.logout(account.realmId.value, account.id.value)
        } catch (cause: Exception) {
            /** Not interests */
        }
    }
}

data class AccessToken(
    val accessToken: String,
    val expiresIn: Long,
    val type: String,
    val refreshToken: String,
    val refreshExpiresIn: Long,
)
