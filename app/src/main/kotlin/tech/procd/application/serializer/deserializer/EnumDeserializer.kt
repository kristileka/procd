package tech.procd.application.serializer.deserializer

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleDeserializers

class EnumDeserializer<T : Enum<T>>(private val enum: Class<T>) : StdDeserializer<T>(enum) {
    private val valuesMap: Map<String, String> = getEnumValuesWithNamesFromJsonValueAnnotation(enum)
    private val namesMap: Map<String, T> = enum.enumConstants.associateBy { it.name }

    override fun deserialize(
        parser: com.fasterxml.jackson.core.JsonParser?,
        context: DeserializationContext?
    ): T {
        val value = parser?.valueAsString

        val result = valuesMap[value]
            ?.let { namesMap[it] }
            ?: namesMap[value?.uppercase()]

        return result ?: throw JsonParseException(
            parser,
            "Invalid value or name for `${enum}` enum. Allowed are ${valuesMap.keys} or ${namesMap.keys}",
        )
    }
}

@Suppress("unchecked_cast")
class EnumDeserializersResolver : SimpleDeserializers() {
    override fun findEnumDeserializer(
        type: Class<*>?,
        config: DeserializationConfig?,
        beanDesc: BeanDescription?
    ): JsonDeserializer<*>? = if (type?.isEnum == true) {
        EnumDeserializer(type as Class<out Enum<*>>)
    } else {
        null
    }
}

private var reflectionCache: MutableMap<Class<*>, Map<String, String>> = mutableMapOf()

private fun <T : Enum<T>> getEnumValuesWithNamesFromJsonValueAnnotation(enumClass: Class<T>): Map<String, String> =
    reflectionCache[enumClass]
        ?: run {
            val values = enumClass.enumConstants.mapNotNull { enumConstant ->
                try {
                    val valueField = enumClass.getDeclaredField("value")
                    val annotation = valueField.getAnnotation(JsonValue::class.java)

                    if (annotation != null) {
                        valueField.isAccessible = true
                        valueField.get(enumConstant) as String to enumConstant.name
                    } else {
                        null
                    }
                } catch (e: NoSuchFieldException) {
                    null
                }
            }.toMap()

            reflectionCache[enumClass] = values

            values
        }
