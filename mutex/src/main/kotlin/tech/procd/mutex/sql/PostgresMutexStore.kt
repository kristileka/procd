package tech.procd.mutex.sql

import tech.procd.persistence.postgres.execute
import tech.procd.persistence.postgres.fetchOne
import tech.procd.persistence.postgres.querybuilder.delete
import tech.procd.persistence.postgres.querybuilder.insert
import tech.procd.persistence.postgres.querybuilder.select
import tech.procd.persistence.postgres.withException
import io.vertx.sqlclient.SqlClient
import java.time.LocalDateTime
import java.util.*

class PostgresMutexStore(
    private val sqlClient: SqlClient
) {
    suspend fun touch(id: UUID, withLifeTime: LocalDateTime) = sqlClient.withException {
        execute(
            insert(
                to = "infrastructure_mutex",
                rows = mapOf(
                    "id" to id,
                    "expire_date" to withLifeTime,
                ),
            )
        )

        Unit
    }

    suspend fun has(id: UUID): Boolean = sqlClient.withException {
        fetchOne(
            select("infrastructure_mutex") {
                where {
                    "id" eq id
                    "expire_date" gte LocalDateTime.now()
                }
            },
        )?.getUUID("id") != null
    }

    suspend fun remove(id: UUID) = sqlClient.withException {
        execute(
            delete("infrastructure_mutex") {
                where {
                    "id" eq id
                }
            },
        )

        Unit
    }
}
