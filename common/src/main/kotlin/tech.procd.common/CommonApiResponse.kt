package tech.procd.common

data class CommonApiResponse<T>(
    val data: T? = null,
    val errors: Map<String, String>? = null
)
