package tech.procd.persistence.postgres.querybuilder

data class CompiledQuery(
    val sql: String,
    val statements: List<Any?>
)

interface Query {
    fun compile(): CompiledQuery
}

class QueryGenerationFailed(message: String) : Exception(message)
