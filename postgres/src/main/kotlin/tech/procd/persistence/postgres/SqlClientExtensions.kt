@file:Suppress("Unused")

package tech.procd.persistence.postgres

import tech.procd.persistence.postgres.querybuilder.CompiledQuery
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Tuple

/**
 * Executes the specified query.
 *
 * @throws [PersistenceException.InteractingFailed]
 * @throws [PersistenceException.UniqueConstraintViolationCheckFailed]
 */
suspend fun SqlClient.execute(compiledQuery: CompiledQuery): RowSet<Row> = withException {
    val preparedQuery = preparedQuery(compiledQuery.sql)

    preparedQuery.execute(Tuple.tuple(compiledQuery.statements)).await()
}

/**
 * Executes the specified query (in transaction context).
 *
 * @throws [PersistenceException.InteractingFailed]
 * @throws [PersistenceException.UniqueConstraintViolationCheckFailed]
 */
suspend fun PgPool.execute(compiledQuery: CompiledQuery): RowSet<Row> = withException {
    val connection = connection.await()

    try {
        val preparedQuery = connection.preparedQuery(compiledQuery.sql)

        preparedQuery.execute(Tuple.tuple(compiledQuery.statements)).await()
    } finally {
        connection.close()
    }
}

/**
 * Executes the specified query and returns one entity as a result
 *
 * @throws [PersistenceException.InteractingFailed]
 */
suspend fun SqlClient.fetchOne(compiledQuery: CompiledQuery): Row? = withException {
    val preparedQuery = preparedQuery(compiledQuery.sql)
    val resultSet = preparedQuery.execute(Tuple.tuple(compiledQuery.statements)).await()

    resultSet.firstOrNull()
}

suspend fun SqlClient.sequence(name: String): Long = withException {
    val preparedQuery = preparedQuery("SELECT nextval('$name') as sequence_value")
    val resultSet = preparedQuery.execute().await()

    return resultSet.firstOrNull()?.getLong("sequence_value")
        ?: error("Unable to obtain `$name` sequence value")
}

/**
 * Executes the specified query and returns an list of entities as a result.
 *
 * @throws [PersistenceException.InteractingFailed]
 */
suspend fun SqlClient.fetchAll(compiledQuery: CompiledQuery): List<Row> = withException {
    val preparedQuery = preparedQuery(compiledQuery.sql)
    val resultSet = preparedQuery.execute(Tuple.tuple(compiledQuery.statements)).await()

    resultSet.toList()
}

/**
 * Executes the code specified in the block within a transaction.
 *
 * @throws [PersistenceException.UniqueConstraintViolationCheckFailed]
 * @throws [PersistenceException.InteractingFailed]
 * @throws [Exception]
 */
suspend fun <R> PgPool.transaction(code: suspend SqlConnection.() -> R): R = withException {
    val connection = connection.await()
    val transaction = connection.begin().await()

    try {
        code(connection).also {
            transaction.commit().await()
        }
    } finally {
        connection.close()
    }
}

suspend fun SqlClient.batchInsert(instructions: BatchInsert): RowSet<Row> = withException {
    var cursorPosition = 0
    val queryStringBuilder = StringBuilder().apply {
        append(
            "INSERT INTO ${instructions.to} (${instructions.fields.joinToString(", ")}) ",
        )

        append("VALUES ")

        append(
            instructions.values.joinToString(", ") {
                val placeholders = (1..it.size).map { "$${++cursorPosition}" }
                "(${placeholders.joinToString(", ")})"
            },
        )

        instructions.onConflictAction?.let { conflict ->
            append(" ON CONFLICT (${conflict.conflictColumns.joinToString(",")}) DO ")

            when (val action = conflict.action) {
                is BatchInsert.Conflict.Action.DoNothing -> append("NOTHING ")
                is BatchInsert.Conflict.Action.Update -> {
                    append("UPDATE SET ")
                    append(
                        action.updateColumns.joinToString(", ") { "$it = EXCLUDED.$it" },
                    )
                }
            }
        }
    }

    preparedQuery(queryStringBuilder.toString())
        .execute(Tuple.tuple(instructions.values.flatten()))
        .await()
}

data class BatchInsert(
    val to: String,
    val fields: List<String>,
    val values: List<List<Any?>>,
    val onConflictAction: Conflict? = null
) {
    data class Conflict(
        val conflictColumns: List<String> = listOf(),
        val action: Action
    ) {
        sealed class Action {
            object DoNothing : Action()
            class Update(
                val updateColumns: List<String>
            ) : Action()
        }
    }
}
