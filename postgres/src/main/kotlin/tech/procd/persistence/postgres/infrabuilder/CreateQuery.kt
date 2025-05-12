package tech.procd.persistence.postgres.infrabuilder

import tech.procd.persistence.postgres.querybuilder.CompiledQuery
import tech.procd.persistence.postgres.querybuilder.Query
import tech.procd.persistence.postgres.querybuilder.QueryGenerationFailed

@Suppress("Unused")
class CreateQuery(
    private val tableName: String,
    private val columns: Map<String, ValueDefinition>
) : Query {

    init {
        if (tableName.isBlank()) {
            throw QueryGenerationFailed("Table name must be specified")
        }

        if (columns.isEmpty()) {
            throw QueryGenerationFailed("Create table without rows cant be possible")
        }
    }

    override fun compile(): CompiledQuery {

        val updatedFields =
            columns.map { (column, definition) -> "$column $definition" }.joinToString(", ")

        return CompiledQuery(
            sql = "CREATE TABLE $tableName ($updatedFields);",
            statements = listOf()
        )
    }
}
