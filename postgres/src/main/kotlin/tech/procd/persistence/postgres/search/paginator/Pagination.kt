@file:Suppress("MatchingDeclarationName")

package com.tech22.app.search.paginator

data class Page<T>(
    val rows: List<T>,
    val totals: Any = mapOf<Any, Any>(),
    val pagination: Pagination
) {
    data class Pagination(
        val page: Int,
        val perPage: Int,
        val total: Int,
        val totalPages: Int = total
    )


    fun <K> copy(rows: List<K>): Page<K> {
        return Page(rows, totals, pagination)
    }

    /**
     * This function transforms the Paginator from a regular type to a new type when
     * provided with the transformation.
     */
    fun <K> copy(searchSumTransformer: (T) -> K): Page<K> {
        return Page(rows.map(searchSumTransformer), totals, pagination)
    }
}
