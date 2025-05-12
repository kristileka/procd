package tech.procd.persistence.postgres.querybuilder

import tech.procd.persistence.postgres.infrabuilder.CreateQuery
import tech.procd.persistence.postgres.infrabuilder.ValueDefinition

/**
 * Usage example:
 *
 * val query = select("table_name tn", listOf("tn.*", "tn.*")) {
 *    limit = 100
 *    offset = 0
 *
 *    where {
 *        "tn.user_id" eq userId
 *    }
 *
 *    orWhere {
 *        "at.status" contains listOf("active", "banned")
 *        "tn.user_id" notEq userId
 *    }
 *
 *    "cr.age" orderBy "ASC"
 *    "another_table at" leftJoin "tn.form_id = at.id"
 * }
 */
fun select(
    from: String,
    columns: List<String> = listOf("*"),
    code: (SelectQuery.() -> Unit)? = null
): CompiledQuery {
    val query = SelectQuery(from, columns)

    if (code != null) query.apply(code)

    return query.compile()
}

/**
 * Usage example:
 *
 * val query = insert("table", mapOf("field" to "value", "another_field" to 3.14, "created_at" to null)) {
 *     onConflict(
 *        listOf("field"),
 *        OnConflict.DoUpdate(mapOf("field" to "anotherValue", "created_at" to currentDate))
 *     )
 * }
 */
fun insert(
    to: String,
    rows: Map<String, Any?>,
    code: (InsertQuery.() -> Unit)? = null
): CompiledQuery {
    val query = InsertQuery(to, rows)

    if (code != null) query.apply(code)

    return query.compile()
}

fun create(
    table: String,
    columns: Map<String, ValueDefinition>,
): CompiledQuery {
    val query = CreateQuery(table, columns)
    return query.compile()
}

/**
 * Usage example:
 *
 * val query = update("table_name", mapOf("title" to "newTitle", "score" to 100)) {
 *     where {
 *         "id" eq uuid
 *     }
 * }
 */
fun update(on: String, rows: Map<String, Any?>, code: (UpdateQuery.() -> Unit)): CompiledQuery {
    val query = UpdateQuery(on, rows)

    query.apply(code)

    return query.compile()
}

fun delete(from: String, code: (DeleteQuery.() -> Unit) = {}): CompiledQuery {
    val query = DeleteQuery(from)

    query.apply(code)

    return query.compile()
}
