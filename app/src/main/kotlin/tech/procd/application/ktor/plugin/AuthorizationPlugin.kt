package tech.procd.application.ktor.plugin

import tech.procd.customer.account.auth.AuthException
import tech.procd.customer.account.auth.AuthService
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val challengePhase = PipelinePhase("Challenge")
private val authorizationPhase = PipelinePhase("Authorization")

class AuthorizationPlugin(configuration: Configuration) {
    private val authService: AuthService = configuration.authService

    class Configuration {
        lateinit var authService: AuthService

        fun configure(authService: AuthService) {
            this.authService = authService
        }
    }

    fun interceptPipeline(
        pipeline: ApplicationCallPipeline,
        expectedRole: String,
    ) {
        pipeline.insertPhaseAfter(Plugins, challengePhase)
        pipeline.insertPhaseAfter(challengePhase, authorizationPhase)

        pipeline.intercept(authorizationPhase) {
            val accessToken: HttpAuthHeader.Single = call.request.accessToken()

            if (!authService.verifyToken(accessToken.blob)) {
                logger.debug("Failed to verify received token")

                throw AuthException.IncorrectAuthToken
            }

            val principal = authService.extractPrincipal(accessToken)

            this.context.authentication.principal(principal)

            if (expectedRole in principal.assignedRoles) {
                return@intercept proceed()
            }

            throw AuthException.NotAllowed(expectedRole)
        }
    }

    companion object Plugin :
        BaseApplicationPlugin<ApplicationCallPipeline, Configuration, AuthorizationPlugin> {
        override val key = AttributeKey<AuthorizationPlugin>("Authorization Plugin")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): AuthorizationPlugin {
            val configuration = Configuration().apply(configure)

            pipeline.insertPhaseAfter(Plugins, challengePhase)
            pipeline.insertPhaseAfter(challengePhase, authorizationPhase)

            return AuthorizationPlugin(configuration)
        }
    }

    private suspend fun ApplicationRequest.accessToken(): HttpAuthHeader.Single = try {
        authService.extractHeader(call.request)
            ?: error("Unable to find auth header value")
    } catch (cause: Exception) {
        logger.debug("Unable to extract auth header: {}", cause.message)

        throw AuthException.IncorrectAuthToken
    }
}
