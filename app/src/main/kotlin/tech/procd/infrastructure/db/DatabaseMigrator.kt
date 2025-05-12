package tech.procd.infrastructure.db

import tech.procd.persistence.postgres.StorageConfiguration
import tech.procd.persistence.postgres.jdbcDSN
import tech.procd.infrastructure.MigrationException
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.exception.FlywaySqlException

interface Migrator {
    fun migrate()
}

class DatabaseMigrator(
    private val configuration: StorageConfiguration,
    private val historyTableName: String = "infrastructure_migrations_db",
    private val migrationsDirectory: String = "/migrations/database"
) : Migrator {

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val DEFAULT_AWAIT_DELAY = 1_000L
        private const val MAX_CONNECTION_ATTEMPT = 15
    }

    private var connectionAttempt: Int = 1

    override fun migrate() {
        logger.info("Applying Database Migration")

        try {
            execute()
        } catch (cause: FlywaySqlException) {
            logger.error("Unable to connect to database: {}", cause.message)

            if (cause.message?.contains("connect") == false) {
                throw cause
            }

            if (connectionAttempt > MAX_CONNECTION_ATTEMPT) {
                throw MigrationException.ApplyingMigrationFailed(cause.message ?: "")
            }

            connectionAttempt++

            logger.info("Await connection to database. Current attempt: {}", connectionAttempt)

            Thread.sleep(DEFAULT_AWAIT_DELAY)

            migrate()
        } catch (cause: Exception) {
            logger.error("Unable to process migration: {}", cause.message)

            throw cause
        }

        logger.info("database migrations completed")
    }

    private fun execute() {
        flyway()
            .table(historyTableName)
            .locations(migrationsDirectory)
            .load()
            .migrate()
    }

    private fun flyway() = Flyway
        .configure()
        .baselineOnMigrate(true)
        .outOfOrder(true)
        .dataSource(configuration.jdbcDSN(), configuration.username, configuration.password)
}
