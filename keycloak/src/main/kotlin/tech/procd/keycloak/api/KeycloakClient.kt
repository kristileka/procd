package tech.procd.keycloak.api

import com.fasterxml.jackson.databind.ObjectMapper
import tech.procd.keycloak.KeycloakConfiguration
import tech.procd.keycloak.KeycloakExceptions
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import mu.KotlinLogging

interface KeycloakClient {

    @Suppress("MaxLineLength")
    companion object AuthorizationProperties {
        /**
         *  A string indicating the format of the token specified in the claim_token parameter
         */
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:uma-ticket"

        /**
         *  string value indicating how the server should respond to authorization requests. This parameter is
         *  specially useful when you are mainly interested in either the overall decision or the permissions granted
         *  by the server, instead of a standard OAuth2 response. Possible values are:
         *
         *   - decision: Indicates that responses from the server should only represent the overall decision by returning a JSON
         *   - permissions: Indicates that responses from the server should contain any permission granted by the server by returning a JSON
         */
        const val RESPONSE_MODE = "decision"
    }

    /**
     * Checks if the specified action is available for the current token owner.
     *
     * @throws [KeycloakExceptions.OperationFailed]
     */
    suspend fun allowed(token: String, vararg permissions: Permission): Boolean

    /**
     * Obtain JWT token.
     *
     * @throws [KeycloakExceptions.UnableToGetToken]
     * @throws [KeycloakExceptions.OperationFailed]
     */
    suspend fun token(username: String, password: String): KeycloakAccessToken

    /**
     * Refresh JWT token.
     *
     * @throws [KeycloakExceptions.UnableToGetToken]
     * @throws [KeycloakExceptions.OperationFailed]
     */
    suspend fun refresh(token: String): KeycloakAccessToken

    suspend fun verify(token: String): Boolean
}

class DefaultKeycloakClient(
    private val configuration: KeycloakConfiguration,
    private val objectMapper: ObjectMapper,
) : KeycloakClient {

    private companion object {
        private val logger = KotlinLogging.logger {}
        private val ignoredStatuses = listOf(
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Conflict,
            HttpStatusCode.BadRequest,
        )
    }

    @Suppress("MagicNumber")
    private val httpClient by lazy {
        HttpClient(CIO) {
            if (this@DefaultKeycloakClient.configuration.logInteractions) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.ALL
                }
            }

            expectSuccess = false
        }
    }

    override suspend fun allowed(token: String, vararg permissions: Permission): Boolean = try {
        val response = httpClient.submitForm(
            url = configuration.tokenUrl,
            formParameters = Parameters.build {
                set("grant_type", KeycloakClient.GRANT_TYPE)
                set("audience", configuration.apiClientInfo.id)
                set("response_mode", KeycloakClient.RESPONSE_MODE)

                permissions.forEach {
                    append("permission", it.toString())
                }
            },
        ) {
            header("Authorization", "Bearer $token")
        }

        response.status.isSuccess().also {
            if (!it) {
                logger.error(
                    "The action is prohibited for this token (expected permissions {}): {}",
                    permissions.toList(), response,
                )
            }
        }
    } catch (e: Exception) {
        throw KeycloakExceptions.OperationFailed(configuration.activeRealm, e)
    }

    override suspend fun token(username: String, password: String): KeycloakAccessToken {
        val response = httpClient.submitForm(
            url = configuration.tokenUrl,
            formParameters = Parameters.build {
                set("username", username)
                set("password", password)
                set("grant_type", "password")
                set("scope", "offline_access")
                set("client_id", configuration.apiClientInfo.id)

                if (configuration.apiClientInfo.secret != null) {
                    set("client_secret", configuration.apiClientInfo.secret)
                }
            },
        )

        return response.asAccessTokenResponse()
    }

    override suspend fun refresh(token: String): KeycloakAccessToken {
        val response = httpClient.submitForm(
            url = configuration.tokenUrl,
            formParameters = Parameters.build {
                set("refresh_token", token)
                set("grant_type", "refresh_token")
                set("scope", "offline_access")
                set("client_id", configuration.apiClientInfo.id)

                if (configuration.apiClientInfo.secret != null) {
                    set("client_secret", configuration.apiClientInfo.secret)
                }
            },
        )

        return response.asAccessTokenResponse()
    }

    override suspend fun verify(token: String): Boolean {
        val response = httpClient.get {
            url(configuration.infoUrl)
            headers {
                append("Accept", "application/json")
                append("Authorization", "Bearer $token")
            }
        }

        return response.status.isSuccess()
    }

    /**
     * @throws [KeycloakExceptions.UnableToGetToken]
     */
    private suspend fun HttpResponse.asAccessTokenResponse(): KeycloakAccessToken {
        val jsonResponse = bodyAsText()

        if (!status.isSuccess()) {
            if (status !in ignoredStatuses) {
                logger.error("Keycloak error response ({}): {}", status.value, jsonResponse)
            }

            val responseData = objectMapper.readValue(
                jsonResponse,
                KeycloakErrorResponse::class.java,
            )

            if (responseData.errorDescription?.contains("Account disabled") == true) {
                throw KeycloakExceptions.AccountDisabled
            }

            throw KeycloakExceptions.UnableToGetToken(
                responseData.errorDescription ?: responseData.error,
            )
        }

        return objectMapper.readValue(jsonResponse, KeycloakAccessToken::class.java)
    }
}
