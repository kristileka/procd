package com.tech22.app.search.paginator

import kotlin.math.max

typealias PaginationBuilder = (total: Int, max: Int) -> Page.Pagination

class Paginator<T>(
    private val perPage: Int,
    private val fetch: suspend (range: IntRange, PaginationBuilder) -> T
) {
    suspend fun page(pageIndex: Int): T {
        val range = calculateRange(pageIndex)

        return fetch(range) { total, _ ->
            createPagination(pageIndex, total)
        }
    }

    private fun calculateRange(pageIndex: Int): IntRange {
        val begin = max(0, (pageIndex - 1) * perPage)
        val end = pageIndex * perPage - 1

        return IntRange(begin, end)
    }

    private fun createPagination(pageIndex: Int, total: Int): Page.Pagination {
        val totalPages = calculateTotalPages(total)

        return Page.Pagination(
            perPage = perPage,
            page = pageIndex,
            total = total,
            totalPages = totalPages,
        )
    }

    private fun calculateTotalPages(total: Int): Int {
        if (total <= 1) {
            return 1
        }

        val totalPages = (total / perPage)

        return if (totalPages > 0) totalPages else 1
    }
}
