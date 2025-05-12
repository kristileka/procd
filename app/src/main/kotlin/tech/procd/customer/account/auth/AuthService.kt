package tech.procd.customer.account.auth

import com.fasterxml.jackson.databind.ObjectMapper
import tech.procd.customer.account.Account
import tech.procd.keycloak.KeycloakConfiguration
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import org.keycloak.TokenVerifier
import org.keycloak.jose.jwk.JSONWebKeySet
import org.keycloak.jose.jwk.JWKParser
import org.keycloak.representations.AccessToken
import java.net.URL
import java.security.PublicKey

interface AuthService {
    suspend fun extractHeader(request: ApplicationRequest): HttpAuthHeader.Single?
    suspend fun extractPrincipal(accessToken: HttpAuthHeader): Account
    suspend fun verifyToken(accessToken: String): Boolean
}

class KeycloakAuthService(
    private val configuration: KeycloakConfiguration,
    private val extractor: TokenExtractor,
) : AuthService {

    override suspend fun extractPrincipal(accessToken: HttpAuthHeader): Account {
        return try {
            extractor.extractAccount(accessToken)
                ?: error("Unable to find account for specified token")
        } catch (cause: Exception) {
            throw AuthException.Failed(cause.message ?: "Unknown reason")
        }
    }

    override suspend fun extractHeader(request: ApplicationRequest): HttpAuthHeader.Single? {
        val authorizationHeader = request.parseAuthorizationHeader() ?: return null

        return when (authorizationHeader) {
            is HttpAuthHeader.Single -> authorizationHeader
            is HttpAuthHeader.Parameterized -> throw AuthException.IncorrectAuthToken
            else -> null
        }
    }

    override suspend fun verifyToken(accessToken: String): Boolean = try {
        val tokenVerifier = createTokenVerifier(accessToken)
        val publicKey = extractPublicKey(tokenVerifier.header.keyId)
            ?: throw AuthException.NoPublicKeyFound

        tokenVerifier.publicKey(publicKey)
        tokenVerifier.verify().token != null
    } catch (cause: Exception) {
        false
    }

    private fun createTokenVerifier(accessToken: String): TokenVerifier<AccessToken> = TokenVerifier
        .create(accessToken, AccessToken::class.java)
        .withChecks(
            TokenVerifier.RealmUrlCheck(configuration.publicRealmUrl),
            TokenVerifier.SUBJECT_EXISTS_CHECK,
            TokenVerifier.IS_ACTIVE,
        )

    private fun extractPublicKey(keyId: String): PublicKey? =
        ObjectMapper().readValue(
            URL(configuration.certificateUrl).openStream(),
            JSONWebKeySet::class.java,
        )?.keys
            ?.firstOrNull { it.keyId == keyId }
            ?.let { JWKParser.create(it).toPublicKey() }
}
