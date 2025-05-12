package tech.procd.persistence.postgres

import io.vertx.pgclient.PgException
import io.vertx.sqlclient.SqlClient

sealed class PersistenceException(message: String?) : Exception(message) {
    class UniqueConstraintViolationCheckFailed(message: String?) : PersistenceException(message)
    class InteractingFailed(withMessage: String) : PersistenceException(withMessage)
}

internal fun PgException.isUniqueConstraintFailed(): Boolean =
    sqlState == "23505" || (message?.contains("duplicate key") ?: false)

fun PgException.adapt(): PersistenceException {
    if (isUniqueConstraintFailed()) {
        return PersistenceException.UniqueConstraintViolationCheckFailed(this.message)
    }

    return PersistenceException.InteractingFailed(message.toString())
}

inline fun <R> SqlClient.withException(code: SqlClient.() -> R): R = try {
    code()
} catch (e: PgException) {
    throw e.adapt()
}
