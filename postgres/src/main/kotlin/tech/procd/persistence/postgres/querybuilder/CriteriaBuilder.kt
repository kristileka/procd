@file:Suppress("MatchingDeclarationName", "unused")

package tech.procd.persistence.postgres.querybuilder

import java.time.LocalDate
import java.time.LocalDateTime

@Suppress("TooManyFunctions")
class CriteriaBuilder(
    private var cursorPosition: Int = 0
) {
    private val patterns: MutableList<String> = mutableListOf()
    private val statements: MutableList<Any?> = mutableListOf()

    fun cursorPosition() = cursorPosition
    fun fromPosition(position: Int): CriteriaBuilder {
        cursorPosition = position

        return this
    }

    infix fun String.between(range: ClosedRange<LocalDateTime>) = add(
        "$this BETWEEN $${++cursorPosition} AND $${++cursorPosition}",
        listOf(range.start, range.endInclusive),
    )

    infix fun String.notBetween(range: ClosedRange<LocalDateTime>) = add(
        "$this NOT BETWEEN $${++cursorPosition} AND $${++cursorPosition}",
        listOf(range.start, range.endInclusive),
    )

    fun String.emptyArray() = add(
        "$this = '{}'",
    )

    infix fun String.anyIn(values: List<Any>) = add(buildAnyInCriteria(this, values, false))
    infix fun String.notAnyIn(values: List<String>) = add(buildAnyInCriteria(this, values, true))

    infix fun String.contains(values: List<Any?>) = add(
        "$this IN (${values.joinToString(", ") { "'$it'" }})",
        Unit,
    )

    infix fun String.notContains(values: List<Any?>) = add(
        "$this NOT IN (${values.joinToString(", ") { "'$it'" }})",
        Unit,
    )

    infix fun String.eq(value: Any?) = if (value != null) {
        add("$this = $${++cursorPosition}", value)
    } else {
        add("$this IS NULL")
    }

    infix fun String.notEq(value: Any?) = if (value != null) {
        add("$this != $${++cursorPosition}", value)
    } else {
        add("$this IS NOT NULL")
    }

    infix fun String.gt(value: LocalDate) = add("$this > $${++cursorPosition}", value)
    infix fun String.gte(value: LocalDate) = add("$this >= $${++cursorPosition}", value)
    infix fun String.lt(value: LocalDate) = add("$this < $${++cursorPosition}", value)
    infix fun String.lte(value: LocalDate) = add("$this <= $${++cursorPosition}", value)
    infix fun String.gt(value: LocalDateTime) = add("$this > $${++cursorPosition}", value)
    infix fun String.gte(value: LocalDateTime) = add("$this >= $${++cursorPosition}", value)
    infix fun String.lt(value: LocalDateTime) = add("$this < $${++cursorPosition}", value)
    infix fun String.lte(value: LocalDateTime) = add("$this <= $${++cursorPosition}", value)
    infix fun String.gt(value: Number) = add("$this > $${++cursorPosition}", value)
    infix fun String.gte(value: Number) = add("$this >= $${++cursorPosition}", value)
    infix fun String.lt(value: Number) = add("$this < $${++cursorPosition}", value)
    infix fun String.lte(value: Number) = add("$this <= $${++cursorPosition}", value)
    infix fun String.like(value: String) = add("$this ILIKE $${++cursorPosition}", "%$value%")
    infix fun String.lLike(value: String) = add("$this ILIKE $${++cursorPosition}", "%$value")
    infix fun String.rLike(value: String) = add("$this ILIKE $${++cursorPosition}", "$value%")

    fun has() = patterns.isNotEmpty()
    fun build(): CompiledQuery {
        var result = ""

        var isFirstElement = true

        patterns.forEach {
            result += if (isFirstElement) it else " AND $it"
            isFirstElement = false
        }

        return CompiledQuery(sql = result, statements = statements)
    }

    private fun add(pattern: String, value: Any? = Unit) {
        patterns.add(pattern)
        if (value !is Unit) {
            statements.add(value)
        }
    }

    private fun buildAnyInCriteria(
        field: String,
        values: List<Any>,
        not: Boolean = false
    ): String {
        val stringBuilder = StringBuilder("(")

        values.forEach {
            if (not) {
                stringBuilder.append("NOT ")
            }

            stringBuilder.append("('$it' = ANY($field)) OR ")
        }

        stringBuilder.append(")")

        return stringBuilder.toString().replace(Regex(" OR \\)$"), ")")
    }
}

internal fun buildWhereSection(
    whereCriteria: CriteriaBuilder,
    orWhereCriteria: CriteriaBuilder
): CompiledQuery {
    @Suppress("TooGenericExceptionThrown")
    if (!whereCriteria.has() && orWhereCriteria.has()) {
        throw Exception("The OR section cannot be filled if the main condition section is empty")
    }

    if (!whereCriteria.has()) {
        return CompiledQuery(sql = "", statements = listOf())
    }

    val where = whereCriteria.build()
    val orWhere = orWhereCriteria.build()

    val statements: List<Any?> = where.statements.plus(orWhere.statements)
    var sql = " WHERE (${where.sql})"

    if (orWhere.sql.isNotEmpty()) {
        sql += " OR (${orWhere.sql})"
    }

    return CompiledQuery(sql = sql, statements = statements)
}
