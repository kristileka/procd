package tech.procd.common

const val DEFAULT_PAGE_SIZE = 25

data class SearchQuery(
    val criteria: List<Criteria>,
    val orderBy: OrderBy?,
    val page: Int = 1,
    val perPage: Int = DEFAULT_PAGE_SIZE
) {
    data class Criteria(
        val field: String,
        val type: String,
        val value: Any?
    )

    data class OrderBy(
        val field: String,
        val direction: String
    )
}
