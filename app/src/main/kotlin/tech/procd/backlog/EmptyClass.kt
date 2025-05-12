//
//    post("/get") {
//        sqlClient.execute(
//            create(
//                "asd", mapOf(
//                    "id" to ValueDefinition("uuid"),
//                    "first_name" to ValueDefinition("varchar", 255),
//                    "last_name" to ValueDefinition("varchar", 255),
//                )
//            )
//        )
//        call.respond(
//            HttpStatusCode.OK,
//            CommonApiResponse(
//                data = mapOf(
//                    "id" to "123",
//                ),
//            ),
//        )
//    }
//
//    post("insert") {
//        sqlClient.execute(
//            insert(
//                "asd", mapOf(
//                    "id" to UUID.randomUUID(),
//                    "first_name" to "John",
//                    "last_name" to "Doe",
//                )
//            )
//        )
//    }
//
//    post("search") {
//
//        val test = UUID::class.java.name
//
////
////
////        // column_name : id
////        column_name : test
////        column_type : varchar
////        column_class : UUID
////        table_name: String
////        column_searchValue: String
////        searchable: boolean
//
//        val searchAdapter = object : SearchAdapter<Map<String, Any>> {
//            override val sqlClient: SqlClient
//                get() = sqlClient
//            override val searchTable: String
//                get() = "asd"
//            override val searchColumns: List<String>
//                get() = listOf("id", "asd.first_name", "asd.last_name")
//            override val searchJoins: List<SearchAdapter.JoinInstructions>
//                get() = listOf()
//            override val searchFieldAssociation: Map<String, String>
//                get() = mapOf("id" to "asd.id", "firstName" to "asd.first_name", "lastName" to "asd.last_name")
//            override val searchAllowedForSorting: List<String>
//                get() = listOf("id", "firstName", "lastName")
//            override val searchTransformer: suspend (Row) -> Map<String, Any>
//                get() = { row ->
//                    mapOf(
//                        "table_name" to row.get(Class.forName("java.util.UUID"), "")
//                        "id" to row.getUUID("id"),
//                        "first_name" to row.getString("first_name"),
//                        "last_name" to row.getString("last_name"),
//                    )
//                }
//        }
//
//        val data = searchAdapter.search(
//            SearchQuery(
//                criteria = listOf(
//                    SearchQuery.Criteria(
//                        field = "firstName",
//                        type = "eq",
//                        value = "john",
//                    )
//                ),
//                orderBy = null
//            )
//        )
//        call.respond(CommonApiResponse(data.rows))
//    }