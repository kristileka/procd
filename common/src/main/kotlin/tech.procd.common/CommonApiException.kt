package tech.procd.common

@Suppress("SameParameterValue")
sealed class CommonApiException(message: String) : Exception(message) {
    class IncorrectRequestParameters(val violations: Map<String, String>) :
        CommonApiException("Incorrect request data") {
        companion object {
            fun create(field: String, message: String) =
                IncorrectRequestParameters(mapOf(field to message))
        }
    }
}