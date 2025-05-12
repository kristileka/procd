package tech.procd.application.serializer.mapper

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class DateTimeChainDeserializer : JsonDeserializer<LocalDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): LocalDateTime {
        val dateTimeString = p.valueAsString

        return try {
            LocalDateTime.parse(dateTimeString)
        } catch (e: DateTimeParseException) {
            val offsetDateTime = OffsetDateTime.parse(dateTimeString)
            offsetDateTime.toLocalDateTime()
        }
    }
}
