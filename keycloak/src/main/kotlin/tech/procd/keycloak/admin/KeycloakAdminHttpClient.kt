package tech.procd.keycloak.admin

import tech.procd.keycloak.KeycloakConfiguration
import tech.procd.keycloak.KeycloakExceptions
import jakarta.ws.rs.client.ClientRequestContext
import jakarta.ws.rs.client.ClientRequestFilter
import jakarta.ws.rs.client.ClientResponseContext
import jakarta.ws.rs.client.ClientResponseFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ReaderInterceptor
import jakarta.ws.rs.ext.ReaderInterceptorContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClients
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class KeycloakAdminHttpClient(
    private val configuration: KeycloakConfiguration
) {
    private companion object {
        private const val CONNECTION_POOL_SIZE = 10

        private val clientQueue = ArrayBlockingQueue<Keycloak>(CONNECTION_POOL_SIZE)
        private val executorService = Executors.newFixedThreadPool(CONNECTION_POOL_SIZE)
        private val dispatcher = executorService.asCoroutineDispatcher()

        private val logger = KotlinLogging.logger {}
    }

    private var connectionAttempt = AtomicInteger(1)


    init {
        repeat(CONNECTION_POOL_SIZE) {
            clientQueue.add(buildClient())
        }
    }

    suspend fun <T> withClient(action: suspend (Keycloak) -> T): T {
        val client = withContext(Dispatchers.IO) {
            clientQueue.take()
        }

        return withContext(dispatcher) {
            try {
                client.handle(action)
            } finally {
                clientQueue.add(client)
            }
        }
    }

    private suspend fun <T> Keycloak.handle(action: suspend (Keycloak) -> T): T = try {
        action(this)
    } catch (cause: KeycloakExceptions) {
        throw cause
    } catch (cause: Exception) {
        if (connectionAttempt.getAndIncrement() > configuration.maxConnectionAttempts) {
            logger.info(
                "Keycloak operation failed (`{}:{}`) : {}",
                configuration.activeRealm,
                configuration.serverUrl,
                cause.message,
            )

            throw KeycloakExceptions.OperationFailed(configuration.activeRealm, cause)
        }

        logger.debug("Await connection to keycloak. Current attempt: {}", connectionAttempt)

        withContext(Dispatchers.IO) {
            Thread.sleep(configuration.defaultRecoveryDelay)
        }

        handle(action)
    }


    private fun buildClient(): Keycloak {
        val cookieStore = BasicCookieStore()
        val clientBuilder = HttpClients.custom().setDefaultCookieStore(cookieStore)
        val clientHttpEngine = ApacheHttpClient43Engine(clientBuilder.build())
        val resteasyClient = ResteasyClientBuilderImpl()
            .httpEngine(clientHttpEngine)
            .apply {
                if (this@KeycloakAdminHttpClient.configuration.logInteractions) {
                    register(RequestLoggingFilter)
                    register(ResponseLoggingInterceptor)
                }
            }
            .register(ErrorResponseFilter)
            .build()

        return KeycloakBuilder.builder()
            .serverUrl(configuration.serverUrl)
            .realm(configuration.mainRealmName)
            .username(configuration.credentials.username)
            .password(configuration.credentials.password)
            .clientId(configuration.adminClientInfo.id)
            .clientSecret(configuration.adminClientInfo.secret)
            .grantType(OAuth2Constants.PASSWORD)
            .resteasyClient(resteasyClient)
            .build()
    }
}

private object RequestLoggingFilter : ClientRequestFilter {
    private val logger = KotlinLogging.logger {}

    override fun filter(context: ClientRequestContext) {
        logger.debug("Request to Keycloak {}: {} {}", context.uri, context.headers, context.entity)
    }
}

private object ResponseLoggingInterceptor : ReaderInterceptor {
    private val logger = KotlinLogging.logger {}

    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
        val stream = ByteArrayOutputStream()
        val inputStream = context.inputStream

        inputStream.copyTo(stream)
        context.inputStream = ByteArrayInputStream(stream.toByteArray())

        logger.debug("Keycloak response: {}", stream.toString())

        return context.proceed()
    }
}

private object ErrorResponseFilter : ClientResponseFilter {
    private val logger = KotlinLogging.logger {}

    override fun filter(
        requestContext: ClientRequestContext,
        responseContext: ClientResponseContext
    ) {
        if (responseContext.statusInfo.family == Response.Status.Family.SERVER_ERROR) {
            logger.error(
                "Request to Keycloak ({}) resulted in error status: {}",
                requestContext.uri,
                responseContext.status,
            )
        }
    }
}
