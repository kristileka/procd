package tech.procd.api.general.customer.auth

import tech.procd.api.helpers.validated
import tech.procd.api.general.auth.request.RefreshTokenRequest
import tech.procd.api.general.auth.request.TokenRequest
import tech.procd.customer.CustomerStore
import tech.procd.customer.account.auth.AuthProvider
import tech.procd.customer.account.auth.TokenExtractor
import tech.procd.application.ktor.isGranted
import tech.procd.application.ktor.userAccount
import tech.procd.common.CommonApiException
import tech.procd.common.CommonApiResponse
import tech.procd.keycloak.KeycloakExceptions
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

@Suppress("LongMethod", "ThrowsCount")
fun Route.authorizationRoutes() {
    val authProvider: AuthProvider by inject()
    val customerStore: CustomerStore by inject()
    val tokenExtractor: TokenExtractor by inject()

    route("/auth") {
        put {
            val request: TokenRequest = call.validated()

            try {
                val accessToken = authProvider.token(
                    email = request.login,
                    password = request.password,
                )

                val tokenDetails = tokenExtractor.extractTokenDetails(accessToken.accessToken)
                    ?: throw CommonApiException.IncorrectRequestParameters.create(
                        field = "token",
                        message = "Incorrect credentials",
                    )

                call.respond(
                    CommonApiResponse(
                        data = TokenResponse(
                            idUser = tokenDetails.customerId.value,
                            jwtToken = accessToken.accessToken,
                            refreshToken = accessToken.refreshToken,
                        ),
                    ),
                )
            } catch (cause: KeycloakExceptions.AccountDisabled) {
                throw CommonApiException.IncorrectRequestParameters.create(
                    field = "server",
                    message = "Account has been blocked",
                )
            } catch (cause: KeycloakExceptions.UnableToGetToken) {
                throw CommonApiException.IncorrectRequestParameters.create(
                    field = "token",
                    message = "Incorrect credentials",
                )
            }
        }

        put("/refreshToken") {
            val request: RefreshTokenRequest = call.validated()
            val accessToken = authProvider.refresh(request.token)

            call.respond(
                CommonApiResponse(
                    data = RefreshTokenResponse(
                        jwtToken = accessToken.accessToken,
                        refreshToken = accessToken.refreshToken,
                    ),
                ),
            )
        }

        delete {
            authProvider.logout(call.request.parseAuthorizationHeader())

            call.respond(
                CommonApiResponse(
                    data = mapOf("loggedIn" to "0"),
                ),
            )
        }
    }

    isGranted("authenticated") {
        get("/profiles") {
            val customer = customerStore.loadView(call.userAccount().customerId)
                ?: throw CommonApiException.IncorrectRequestParameters.create(
                    "id",
                    "Unable to find customer",
                )

            call.respond(
                CommonApiResponse(
                    data = customer
                ),
            )
        }

        get("/profile") {
            val customer = customerStore.loadView(call.userAccount().customerId)
                ?: throw CommonApiException.IncorrectRequestParameters.create(
                    "id",
                    "Unable to find customer",
                )

            call.respond(
                CommonApiResponse(
                    data = customer
                ),
            )
        }
    }
}

private data class TokenResponse(
    val idUser: UUID,
    val jwtToken: String,
    val refreshToken: String
)


private data class RefreshTokenResponse(
    val jwtToken: String,
    val refreshToken: String
)
