package tech.procd.persistence.postgres.search

import com.tech22.app.search.paginator.Page
import com.tech22.app.search.paginator.Paginator
import io.vertx.pgclient.data.Inet
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import java.math.BigDecimal
import java.net.InetAddress
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tech.procd.common.CommonApiException
import tech.procd.common.DEFAULT_PAGE_SIZE
import tech.procd.common.SearchQuery
import tech.procd.persistence.postgres.cache.InMemoryCache
import tech.procd.persistence.postgres.fetchAll
import tech.procd.persistence.postgres.fetchOne
import tech.procd.persistence.postgres.querybuilder.CriteriaBuilder
import tech.procd.persistence.postgres.querybuilder.select

interface SearchAdapter<T> {
    val sqlClient: SqlClient

    /**
     * Table from which we need to select data.
     */
    val searchTable: String

    /**
     * List of columns to get. Important clarification: columns must be specified with the name of
     * the table in which they are located (dot separator).
     * For example: account.id, customer.currency_code
     */
    val searchColumns: List<String>

    /**
     * List of joined tables.
     */
    val searchJoins: List<JoinInstructions>

    /**
     * On the side of the frontend, they have their own field naming and they are too lazy to
     * change it.
     * This only applies to fields that are searched/sorted (which are specified in the SearchQuery).
     * The key is the name of the field on the frontend side, the value is the name of the field in
     * the database (specifying the table with "." as separator).
     */
    val searchFieldAssociation: Map<String, String>

    /**
     * List of fields (in the internal naming) by which sorting is allowed.
     */
    val searchAllowedForSorting: List<String>

    /**
     * Lambda function for converting query results into the required response format.
     * Used to convert a list of projections.
     */
    val searchTransformer: suspend (Row) -> T

    private val typeDefinitionCache: InMemoryCache<String, Map<String, String>>
        get() = InMemoryCache(
            {
                sqlClient.fetchAll(
                    select("information_schema.columns") {
                        where {
                            "table_name" eq it
                        }
                    },
                ).associate {
                    it.getString("column_name") to it.getString("data_type").lowercase().split(" ")
                        .first()
                }
            },
        )

    data class JoinInstructions(
        val tableName: String,
        val joinType: Type,
        val expression: String
    ) {
        enum class Type {
            LEFT, RIGHT, INNER, FULL
        }
    }

    suspend fun search(request: SearchQuery): Page<T> {
        val criteriaCollection = request.criteria.determineTypes().toMutableList()

        val paginator = Paginator(request.perPage) { range, pagination ->
            val searchResult = sqlClient.search(
                request = request,
                criteriaCollection = criteriaCollection,
                range = range,
            )

            Page(
                pagination = pagination(
                    searchResult.total,
                    min(searchResult.total, DEFAULT_PAGE_SIZE),
                ),
                rows = searchResult.rows,
                totals = emptyMap<String, String>(),
            )
        }

        return paginator.page(request.page)
    }

    private suspend fun SqlClient.search(
        request: SearchQuery,
        criteriaCollection: List<SearchQuery.Criteria>,
        range: IntRange
    ) = coroutineScope {
        val recordCount = async {
            fetchOne(
                buildQuery(
                    fromTable = searchTable,
                    criteriaCollection = criteriaCollection,
                    range = null,
                    columns = listOf("COUNT(*) as total"),
                    orderBy = null,
                ),
            )?.getInteger("total") ?: 0
        }

        val records = async {
            fetchAll(
                buildQuery(
                    fromTable = searchTable,
                    criteriaCollection = criteriaCollection,
                    range = range,
                    columns = searchColumns,
                    orderBy = request.orderBy?.let { Pair(it.field, it.direction) },
                ),
            ).map { searchTransformer(it) }
        }

        SearchResult(
            total = recordCount.await(),
            rows = records.await(),
        )
    }

    @Suppress("LongParameterList")
    private fun buildQuery(
        fromTable: String,
        criteriaCollection: List<SearchQuery.Criteria>,
        range: IntRange?,
        columns: List<String>,
        orderBy: Pair<String, String>?,
    ) = select(
        from = "$fromTable AS $fromTable",
        columns = columns,
    ) {
        searchJoins.forEach { join ->
            val joinedTable = join.tableName
            val joinTableName = "$joinedTable AS $joinedTable"

            when (join.joinType) {
                JoinInstructions.Type.INNER -> joinTableName innerJoin join.expression
                JoinInstructions.Type.RIGHT -> joinTableName rightJoin join.expression
                JoinInstructions.Type.LEFT -> joinTableName leftJoin join.expression
                JoinInstructions.Type.FULL -> joinTableName fullJoin join.expression
            }
        }

        where {
            criteriaCollection.forEach {
                this.add(it)
            }
        }

        if (range != null) {
            limit = range.last - range.first + 1
            offset = range.first
        }

        if (orderBy != null) {
            val associatedField = searchFieldAssociation[orderBy.first]
                ?: throw CommonApiException.IncorrectRequestParameters.create(
                    orderBy.first,
                    "`${orderBy.first}` field not available for sorting",
                )

            if (associatedField !in searchAllowedForSorting) {
                throw CommonApiException.IncorrectRequestParameters.create(
                    orderBy.first, "`${orderBy.first}` field not available for sorting",
                )
            }

            orderBy(associatedField, orderBy.second.uppercase())
        }
    }

    /**
     * Vertx is very jealous of the data type. A simple string can be a UUID, or,
     * for example, an IP address, or a Date/DateTime. And we must be able to pass the correct types.
     */
    private suspend fun List<SearchQuery.Criteria>.determineTypes() = coroutineScope {
        map { async { it.determineType() } }.awaitAll()
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun SearchQuery.Criteria.determineType(): SearchQuery.Criteria {
        val columnParts = searchFieldAssociation[field]?.split(".")
            ?: throw CommonApiException.IncorrectRequestParameters.create(
                field,
                "`${field}` field not available for search",
            )

        if (columnParts.size != 2) {
            error(
                "The field name associated for the search must include the name of the table",
            )
        }

        val tableDefinition = typeDefinitionCache.get(columnParts.first())
            ?: error("Unable to describe table `${columnParts.first()}`")

        if (!tableDefinition.contains(columnParts.last())) {
            val anyPredicate =
                tableDefinition.keys.firstOrNull { columnParts.last().startsWith(it) }

            if (anyPredicate == null || tableDefinition[anyPredicate] != "jsonb") error(
                "`${columnParts.last()}` field not found in `${columnParts.first()}` table",
            )
        }

        val criteriaValue = try {
            when (tableDefinition[columnParts.last()]) {
                "inet" -> value?.toIP()
                "timestamp" -> value?.toDateTime()
                "date" -> value?.toDate()
                "boolean" -> when (value) {
                    is Boolean -> value
                    is String -> value == "1"
                    is Int -> value == 1
                    else -> error("Incorrect boolean value")
                }

                "uuid" -> value?.toUUID()
                "array" -> value?.toListOf() ?: listOf<Any?>()
                "jsonb" -> value
                "integer" -> value as Int
                "smallint" -> value as Int
                "bigint" -> (value as Int).toLong()
                "double" -> value as BigDecimal
                "null" -> null
                else -> (value as String).lowercase()
            }
        } catch (cause: Exception) {
            throw CommonApiException.IncorrectRequestParameters.create(
                field, "Unable to cast `$field` to expected type: $cause",
            )
        }

        return SearchQuery.Criteria(
            field = columnParts.joinToString("."),
            type = type,
            value = criteriaValue,
        )
    }
}

private data class SearchResult<T>(
    val rows: List<T>,
    val total: Int,
    val aggregated: Map<String, BigDecimal> = mapOf()
)

@Suppress("CyclomaticComplexMethod", "Unchecked_Cast")
private fun CriteriaBuilder.add(criteria: SearchQuery.Criteria) {
    val value = criteria.value

    when (criteria.type) {
        "eq" -> criteria.field eq value
        "notEq" -> criteria.field notEq value
        "in" -> criteria.field contains value as List<Any?>
        "contains" -> criteria.field contains value as List<Any?>
        "anyIn" -> criteria.field anyIn value as List<Any>
        "notIn" -> criteria.field notAnyIn value as List<String>
        "like" -> criteria.field like value as String
        "gt" -> when (value) {
            is Number -> criteria.field gt value
            is LocalDate -> criteria.field gt value
            is LocalDateTime -> criteria.field gt value
        }

        "gte" -> when (value) {
            is Number -> criteria.field gte value
            is LocalDate -> criteria.field gte value
            is LocalDateTime -> criteria.field gte value
        }

        "lt" -> when (value) {
            is Number -> criteria.field lt value
            is LocalDate -> criteria.field lt value
            is LocalDateTime -> criteria.field lt value
        }

        "lte" -> when (value) {
            is Number -> criteria.field lte value
            is LocalDate -> criteria.field lte value
            is LocalDateTime -> criteria.field lte value
        }
    }
}

@Suppress("Unchecked_cast")
private fun Any.toListOf() = if (this is List<*>) {
    val firstValue = this.first()

    if (firstValue is String) {
        (this as List<String>).map {
            when {
                it.isUUID() -> it.toUUID()
                else -> it
            }
        }
    } else {
        this
    }
} else {
    error("Value must be a List type")
}

private fun Any.toDate() = try {
    if (this is Temporal) {
        this
    } else {
        LocalDate.parse(this as String, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    }
} catch (cause: Exception) {
    error("Unable to create `date` value from `${this}`: ${cause.message}")
}

private fun Any.toDateTime() = try {
    if (this is LocalDateTime) {
        this
    } else {
        LocalDateTime.ofInstant(Instant.parse(this as String), ZoneId.systemDefault())
    }
} catch (cause: Exception) {
    error("Unable to create `dateTime` value from `${this}`: ${cause.message}")
}

private fun Any.toUUID() = try {
    UUID.fromString(this as String)
} catch (cause: Exception) {
    error("Unable to create `IP` value from `${this}`: ${cause.message}")
}

private fun Any.toIP() = try {
    Inet().setAddress(InetAddress.getByName(this as String))
} catch (cause: Exception) {
    error("Unable to create `IP` value from `${this}`: ${cause.message}")
}

private fun String.isUUID() = try {
    UUID.fromString(this)

    true
} catch (cause: Exception) {
    false
}
