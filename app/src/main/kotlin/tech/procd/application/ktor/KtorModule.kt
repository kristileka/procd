package tech.procd.application.ktor

import tech.procd.api.general.customer.auth.authorizationRoutes
import tech.procd.api.general.customer.registration.customerRegistrationRoutes
import tech.procd.customer.account.auth.AuthException
import tech.procd.customer.account.auth.AuthService
import tech.procd.application.Environment
import tech.procd.application.ktor.plugin.AuthorizationPlugin
import tech.procd.application.ktor.plugin.IpWhiteListPlugin
import tech.procd.application.ktor.plugin.exceptionAttribute
import tech.procd.application.serializer.mapper.configure
import tech.procd.common.CommonApiException
import tech.procd.common.CommonApiResponse
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import mu.KotlinLogging
import org.koin.ktor.ext.inject
import org.slf4j.event.Level
import tech.procd.api.general.company.registration.companyRegistrationRoute

private val logger = KotlinLogging.logger {}

@Suppress("LongMethod")
internal fun Application.ktorModule() {
    val environment: Environment by inject()
    val authService: AuthService by inject()
    val micrometerRegistry: PrometheusMeterRegistry by inject()
    val corsConfiguration: KtorCorsConfiguration by inject()

    install(ForwardedHeaders)
    install(XForwardedHeaders)

    /**
     * We add a special flag to the attributes to indicate that we should force-log a response that contains an error.
     * Even if route response logging is disabled.
     */
    install(StatusPages) {
        exception<CommonApiException.IncorrectRequestParameters> { call, cause ->
            call.attributes.put(exceptionAttribute, true)

            logger.info(
                "Incorrect request parameters: {}",
                cause.violations,
            )

            call.respond(
                HttpStatusCode.BadRequest,
                CommonApiResponse(
                    data = false,
                    errors = cause.violations,
                ),
            )
        }
        exception<AuthException.NotAllowed> { call, _ ->
            call.attributes.put(exceptionAttribute, true)

            call.respond(
                HttpStatusCode.Forbidden,
                CommonApiResponse(
                    data = false,
                    errors = mapOf(
                        "server" to "Access denied",
                    ),
                ),
            )
        }
        exception<AuthException.Restricted> { call, _ ->
            call.attributes.put(exceptionAttribute, true)

            call.respond(
                HttpStatusCode.Forbidden,
                CommonApiResponse(
                    data = false,
                    errors = mapOf(
                        "server" to "Restricted access",
                    ),
                ),
            )
        }
        exception<AuthException.EmailShouldBeVerified> { call, _ ->
            call.attributes.put(exceptionAttribute, true)

            call.respond(
                HttpStatusCode.Forbidden,
                CommonApiResponse(
                    data = false,
                    errors = mapOf(
                        "server" to "You have to verify your email first",
                    ),
                ),
            )
        }
        exception<AuthException.CustomerIsSelfExcluded> { call, _ ->
            call.attributes.put(exceptionAttribute, true)

            call.respond(
                HttpStatusCode.Forbidden,
                CommonApiResponse(
                    data = false,
                    errors = mapOf(
                        "server" to "Customer is self excluded",
                    ),
                ),
            )
        }
        exception<AuthException> { call, cause ->
            val isNotAllowed = cause is AuthException.NotAllowed ||
                    cause is AuthException.Restricted

            call.attributes.put(exceptionAttribute, true)

            call.respond(
                if (isNotAllowed) HttpStatusCode.Forbidden else HttpStatusCode.Unauthorized,
                CommonApiResponse(
                    data = false,
                    errors = mapOf(
                        "server" to "Access denied",
                    ),
                ),
            )
        }
        exception<Throwable> { call, cause ->
            call.attributes.put(exceptionAttribute, true)

            logger.error(
                "Unable to process request: {} {}",
                cause.message,
                cause.stackTraceToString(),
            )

            call.respond(
                HttpStatusCode.InternalServerError,
                CommonApiResponse(
                    data = false,
                    errors = mapOf(
                        "server" to "Unable to process request",
                    ),
                ),
            )
        }

        status(
            HttpStatusCode.NotFound,
            HttpStatusCode.MethodNotAllowed,
            HttpStatusCode.NotAcceptable,
        ) { call, _ ->
            call.attributes.put(exceptionAttribute, true)

            call.respond(
                HttpStatusCode.NotFound,
                CommonApiResponse(
                    data = false,
                    errors = mapOf(
                        "server" to "Unable to find requested route",
                    ),
                ),
            )
        }
    }

    install(CallLogging) {
        level = Level.INFO

        filter { call ->
            !call.request.path().contains("infrastructure")
        }
    }

    install(DefaultHeaders)

    install(ContentNegotiation) {
        jackson {
            configure()
        }
    }

    install(IpWhiteListPlugin)

    install(CORS) {
        allowCredentials = corsConfiguration.allowCredentials
        maxAgeInSeconds = corsConfiguration.maxAge

        if (environment == Environment.PRODUCTION) {
            allowHost(
                host = corsConfiguration.host,
                schemes = corsConfiguration.schemas,
                subDomains = corsConfiguration.subDomains,
            )
        } else {
            anyHost()
        }

        corsConfiguration.allowedMethods.forEach {
            allowMethod(HttpMethod(it))
        }

        corsConfiguration.allowedHeaders.forEach {
            allowHeader(it)
        }
    }

    install(Authentication)

    install(AuthorizationPlugin) {
        configure(authService)
    }

    install(MicrometerMetrics) {
        registry = micrometerRegistry
    }

    routing {
        route("/api") {
            authorizationRoutes()
            customerRegistrationRoutes()
            route("/company") {
                companyRegistrationRoute()
            }
        }
    }
}
