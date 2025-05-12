package tech.procd.application.serializer

import com.fasterxml.jackson.databind.ObjectMapper

interface Serializer {
    fun <T> serialize(structure: T): String
    fun <T> unserialize(payload: String, to: Class<T>): T
}

class JacksonSerializer(
    private val objectMapper: ObjectMapper
) : Serializer {
    override fun <T> serialize(structure: T): String = objectMapper.writeValueAsString(structure)

    override fun <T> unserialize(payload: String, to: Class<T>): T =
        objectMapper.readValue(payload, to)
}
