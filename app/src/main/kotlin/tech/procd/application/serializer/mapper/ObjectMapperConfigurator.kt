package tech.procd.application.serializer.mapper

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import tech.procd.application.serializer.deserializer.EnumDeserializersResolver
import io.vertx.core.json.JsonObject
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.*

fun ObjectMapper.configure(): ObjectMapper {
    registerModule(JavaTimeModule())
    registerModule(
        SimpleModule().addDeserializer(
            LocalDateTime::class.java,
            DateTimeChainDeserializer(),
        ),
    )
    registerModule(
        SimpleModule().apply {
            setDeserializers(EnumDeserializersResolver())
        }
    )

    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true)

    setTimeZone(TimeZone.getTimeZone("UTC"))

    registerModule(
        KotlinModule.Builder().apply {
            configure(KotlinFeature.NullToEmptyCollection, true)
            configure(KotlinFeature.NullToEmptyMap, true)
            configure(KotlinFeature.NullIsSameAsDefault, true)
            configure(KotlinFeature.SingletonSupport, true)
            configure(KotlinFeature.StrictNullChecks, true)
        }.build(),
    )

    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale("NL"))

    return this
}

@Suppress("Unused")
fun ObjectMapper.writeAsVertxJson(value: Any): JsonObject = JsonObject(writeValueAsString(value))

@Suppress("Unused")
fun ObjectMapper.writeAsEmptyVertxJson(): JsonObject =
    JsonObject(writeValueAsString(mapOf<String, String>()))
