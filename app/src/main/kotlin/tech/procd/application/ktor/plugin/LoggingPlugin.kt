package tech.procd.application.ktor.plugin

import com.fasterxml.jackson.databind.json.JsonMapper
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import io.netty.handler.logging.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.util.*
import java.util.zip.GZIPInputStream

private val logger = KotlinLogging.logger {}
private val objectMapper = JsonMapper.builder().build()

private const val MASKED_TEMPLATE = "******"

val traceIdAttribute = AttributeKey<UUID>("x-trace-id")
val exceptionAttribute = AttributeKey<Boolean>("x-exception-thrown")

private val barrierAttribute = AttributeKey<Boolean>("x-barrier")

class LoggingPlugin(configuration: Configuration) {
    private val requestConfiguration = configuration.request()
    private val responseConfiguration = configuration.response()

    class Configuration {
        private var request = Request().build()
        private var response = Response().build()

        fun request() = request.copy()
        fun response() = response.copy()

        fun request(code: Request.() -> Unit) {
            val builder = Request()
            builder.apply(code)

            request = builder.build()
        }

        fun response(code: Response.() -> Unit) {
            val builder = Response()
            builder.apply(code)

            response = builder.build()
        }

        class Request : Common<Request.Configuration>() {
            data class Configuration(
                val logLevel: LogLevel,
                val replacedValues: List<String>,
                val ignoredHeaders: List<String>,
                val maskedHeaders: List<String>,
                val ignoredRoutes: List<String>
            )

            private val ignoredHeaders: MutableList<String> = mutableListOf()
            private val maskedHeaders: MutableList<String> = mutableListOf()

            override fun build() = Configuration(
                logLevel = logLevel,
                replacedValues = replacedValues,
                ignoredHeaders = ignoredHeaders,
                maskedHeaders = maskedHeaders,
                ignoredRoutes = ignoredRoutes,
            )

            fun ignoreHeader(vararg headers: String) {
                ignoredHeaders.addAll(headers)
            }

            fun maskHeader(vararg headers: String) {
                maskedHeaders.addAll(headers)
            }
        }

        class Response : Common<Response.Configuration>() {
            data class Configuration(
                val logLevel: LogLevel = LogLevel.INFO,
                val ignoredRoutes: List<String> = listOf(),
                val replacedValues: List<String> = listOf()
            )

            override fun build() = Configuration(
                logLevel = logLevel,
                ignoredRoutes = ignoredRoutes,
                replacedValues = replacedValues,
            )
        }

        abstract class Common<C> {
            protected var logLevel: LogLevel = LogLevel.INFO
            protected val replacedValues: MutableList<String> = mutableListOf()
            protected val ignoredRoutes: MutableList<String> = mutableListOf()

            abstract fun build(): C

            fun logLevel(level: LogLevel) {
                logLevel = level
            }

            fun replacePayloadValue(vararg values: String) {
                replacedValues.addAll(values)
            }

            fun maskPayloadByUri(vararg urls: String) {
                ignoredRoutes.addAll(urls)
            }
        }
    }

    companion object Plugin :
        BaseApplicationPlugin<ApplicationCallPipeline, Configuration, LoggingPlugin> {
        private lateinit var plugin: LoggingPlugin

        override val key = AttributeKey<LoggingPlugin>("Logging Plugin")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): LoggingPlugin {
            val configuration = Configuration().apply(configure)
            plugin = LoggingPlugin(configuration)

            /** Set the identifier by which we will match the request and response. */
            pipeline.intercept(ApplicationCallPipeline.Setup) {
                call.attributes.put(traceIdAttribute, UUID.randomUUID())
            }

            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                if (!call.request.uri.contains("infrastructure")) {
                    val request = try {
                        call.requestStructure().toJson()
                    } catch (ex: Exception) {
                        "Request is unavailable!"
                    }
                    logger.info(
                        "Received `{}` request: {}",
                        call.attributes[traceIdAttribute],
                        request,
                    )
                }
            }

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Engine) {
                if (!call.request.uri.contains("infrastructure")) {
                    /**
                     * For some reason, this pipeline is executed twice. Let's put a primitive lock
                     * in order to prevent this.
                     */
                    if (!call.attributes.contains(barrierAttribute)) {
                        call.attributes.put(barrierAttribute, true)

                        logger.info(
                            "Processed `{}` response: {}",
                            call.attributes[traceIdAttribute],
                            responseStructure().toJson(),
                        )
                    }
                }
            }

            return plugin
        }

        private suspend fun ApplicationCall.requestStructure(): RequestStructure {
            val payload = if (payloadShouldBeLogged("request")) {
                requestPayload() ?: ""
            } else {
                MASKED_TEMPLATE
            }

            return RequestStructure(
                uri = request.uri,
                method = request.origin.method.value,
                headers = request.headers.filtered(
                    plugin.requestConfiguration.ignoredHeaders,
                    plugin.requestConfiguration.maskedHeaders,
                ),
                payload = payload,
            )
        }

        private suspend fun PipelineContext<Any, ApplicationCall>.responseStructure() =
            ResponseStructure(
                traceId = call.attributes[traceIdAttribute],
                payload = if (call.payloadShouldBeLogged("response")) {
                    responsePayload()?.asClearPayload()
                } else {
                    MASKED_TEMPLATE
                },
            )

        private fun ApplicationCall.payloadShouldBeLogged(type: String): Boolean = when (type) {
            "request" -> plugin.requestConfiguration.ignoredRoutes.none { request.uri.contains(it) }
            "response" -> if (!attributes.contains(exceptionAttribute)) {
                plugin.responseConfiguration.ignoredRoutes.none { request.uri.contains(it) }
            } else {
                true
            }

            else -> false
        }
    }
}

private interface Structure

private data class RequestStructure(
    val method: String,
    val uri: String,
    val headers: Map<String, Any>,
    val payload: String? = null
) : Structure

private data class ResponseStructure(
    val traceId: UUID?,
    val payload: String? = null
) : Structure

@Suppress("ReturnCount")
private suspend fun ApplicationCall.requestPayload(): String? {
    when (request.httpMethod) {
        HttpMethod.Patch, HttpMethod.Put, HttpMethod.Post -> {
            try {
                val formParams: Parameters = receive()

                if (!formParams.isEmpty()) {
                    return objectMapper.writeValueAsString(mapOf("form_data" to formParams.toMap()))
                }
            } catch (cause: Exception) {
                /** Not interests */
            }

            val requestPayload = receiveText()

            if (requestPayload != "") {
                return requestPayload.asClearPayload()
            }
        }
    }

    return null
}

private suspend fun PipelineContext<Any, ApplicationCall>.responsePayload(): String? =
    when (subject) {
        is TextContent -> (subject as TextContent).text
        is OutgoingContent.WriteChannelContent -> {
            val temporaryChannel = ByteChannel(true)
            val contentChannel = subject as OutgoingContent.WriteChannelContent

            runBlocking {
                contentChannel.writeTo(temporaryChannel)
                temporaryChannel.close()
            }

            val clearContent = try {
                val gzipStream = withContext(Dispatchers.IO) {
                    GZIPInputStream(temporaryChannel.toInputStream())
                }
                gzipStream.bufferedReader().use { it.readText() }.also { gzipStream.close() }
            } catch (cause: Exception) {
                temporaryChannel.readRemaining().readText()
            }

            clearContent
        }

        else -> null
    }

private fun Headers.filtered(ignoredHeaders: List<String>, maskedHeaders: List<String>) = toMap()
    .filter { it.key !in ignoredHeaders }
    .mapNotNull {
        val headerValue = it.value.firstOrNull()

        if (headerValue != null) {
            it.key to if (it.key !in maskedHeaders) {
                headerValue
            } else {
                listOf(MASKED_TEMPLATE)
            }
        } else {
            null
        }
    }.toMap()

private fun String.asClearPayload() = try {
    objectMapper.writeValueAsString(
        objectMapper.readValue(this, Map::class.java),
    )
} catch (cause: Exception) {
    this
}

private fun Structure.toJson() = objectMapper.writeValueAsString(this)
