package tech.procd.persistence.postgres

data class StorageConfiguration(
    val driver: String = "postgresql",
    val host: String,
    val port: Int,
    val database: String,
    val username: String? = null,
    val password: String? = null,
    val requireSsl: Boolean = false
)

fun StorageConfiguration.jdbcDSN(): String = "jdbc:$driver://$host:$port/$database"
