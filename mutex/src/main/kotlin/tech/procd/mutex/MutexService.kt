package tech.procd.mutex

import tech.procd.mutex.sql.PostgresMutexStore
import tech.procd.persistence.postgres.PersistenceException
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.time.LocalDateTime
import java.util.*

interface MutexService {
    /**
     * Acquires a lock on the mutex.
     */
    suspend fun <T> acquire(id: UUID, withLifeTime: LocalDateTime, code: suspend () -> T): T
}

private const val AWAIT_DELAY = 500L

class PostgresMutexService(
    private val store: PostgresMutexStore
) : MutexService {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun <T> acquire(
        id: UUID,
        withLifeTime: LocalDateTime,
        code: suspend () -> T
    ): T {
        var acquiredLock = false

        try {
            while (!acquiredLock) {
                try {
                    store.touch(id, withLifeTime)
                    acquiredLock = true
                    logger.trace("`{}` mutex set", id)
                } catch (ex: PersistenceException.UniqueConstraintViolationCheckFailed) {
                    logger.trace("Waiting for the `{}` mutex to be released", id)
                    delay(AWAIT_DELAY)
                }
            }

            return code()
        } finally {
            if (acquiredLock) {
                store.remove(id)
                logger.trace("`{}` mutex released", id)
            }
        }
    }
}

object NullMutexService : MutexService {
    override suspend fun <T> acquire(
        id: UUID,
        withLifeTime: LocalDateTime,
        code: suspend () -> T
    ): T {
        return code()
    }
}

