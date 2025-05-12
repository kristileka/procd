package tech.procd.application.ktor

data class KtorCorsConfiguration(
    val host: String,
    val schemas: List<String>,
    val subDomains: List<String>,
    val allowCredentials: Boolean,
    val maxAge: Long,
    val allowedMethods: List<String>,
    val allowedHeaders: List<String>
)
