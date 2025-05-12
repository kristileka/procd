package tech.procd.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import tech.procd.persistence.postgres.PersistenceException
import tech.procd.persistence.postgres.execute
import tech.procd.persistence.postgres.fetchOne
import tech.procd.persistence.postgres.querybuilder.insert
import tech.procd.persistence.postgres.querybuilder.select
import tech.procd.persistence.postgres.querybuilder.update
import tech.procd.persistence.postgres.transaction
import io.vertx.pgclient.PgPool
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlin.io.path.name

@Suppress("MagicNumber")
abstract class CommonMigration(
    private val sqlClient: PgPool,
    private val historyTableName: String,
    private val migrationsDirectory: String
) {
    companion object {
        val yamlObjectMapper: ObjectMapper = ObjectMapper(YAMLFactory())
            .registerModule(KotlinModule.Builder().build())
        private val hashAlgo = MessageDigest.getInstance("SHA1")
    }

    protected data class MigrationEntry(
        val filePath: String,
        val fileName: String,
        val version: Long,
        val checksum: String,
        val description: String
    ) {
        fun id(): UUID = UUID.nameUUIDFromBytes(fileName.toByteArray())
    }

    abstract fun migrate()

    /**
     * @throws [MigrationException.AlreadyApplied]
     */
    protected fun handle(migration: MigrationEntry, code: suspend () -> Unit): Unit = runBlocking {
        try {
            sqlClient.transaction {
                execute(
                    insert(
                        to = historyTableName,
                        rows = mapOf(
                            "id" to migration.id(),
                            "version_tag" to migration.version,
                            "description" to migration.description,
                            "script" to migration.fileName,
                            "checksum" to migration.checksum,
                        ),
                    ),
                )

                code()

                execute(
                    update(
                        on = historyTableName,
                        rows = mapOf(
                            "installed_on" to LocalDateTime.now(),
                        ),
                    ) {
                        where {
                            "id" eq migration.id()
                        }
                    },
                )
            }
        } catch (cause: PersistenceException.UniqueConstraintViolationCheckFailed) {
            throw MigrationException.AlreadyApplied(migration.fileName)
        }
    }

    /**
     * @throws [MigrationException.IncorrectFile]
     */
    protected fun listMigrations(): List<MigrationEntry> {
        val entities = mutableListOf<MigrationEntry>()

        searchFiles(migrationsDirectory).forEach {
            it.assertCorrectness()

            entities.add(
                MigrationEntry(
                    filePath = it.toString(),
                    fileName = it.name,
                    version = it.migrationVersion(),
                    checksum = it.migrationChecksum(),
                    description = it.migrationDescription(),
                ),
            )
        }

        entities.sortBy { it.version }

        assertIntegrity(entities)

        return entities
    }

    /**
     * @throws [MigrationException.IntegrityCheckFailed]
     */
    private fun assertIntegrity(entities: List<MigrationEntry>) = runBlocking {
        entities.forEach { entry ->
            val expectedChecksum = sqlClient.fetchOne(
                select(historyTableName) {
                    where {
                        "version_tag" eq entry.version
                    }
                },
            )?.getString("checksum")

            if (expectedChecksum != null) {
                if (expectedChecksum != entry.checksum) {
                    throw MigrationException.IntegrityCheckFailed(entry.fileName)
                }
            }
        }
    }

    private fun Path.assertCorrectness() {
        val baseNameParts = name.split("__")

        if (baseNameParts.size != 2) {
            throw MigrationException.IncorrectFile("Incorrect migration file name: $name")
        }

        if (extension != "yaml") {
            throw MigrationException.IncorrectFile(
                "Incorrect migration file extension: `$extension` (should be `yaml`)",
            )
        }
    }

    private fun Path.migrationChecksum() = BigInteger(1, hashAlgo.digest(Files.readAllBytes(this)))
        .toString(16)

    private fun Path.migrationVersion() = name.split("__").first().replace("V", "").toLong()
    private fun Path.migrationDescription() = name.split("__")
        .last()
        .replace(".yaml", "")
        .replace("_", " ")

    private fun searchFiles(inDirectory: String): Stream<Path> {
        val directory = this::class.java.getResource(inDirectory)?.file
            ?: error("Directory `$inDirectory` doesn't exists")

        return Files
            .walk(Paths.get(directory))
            .filter { Files.isRegularFile(it) }
    }
}

sealed class MigrationException(message: String) : Exception(message) {
    class IncorrectFile(message: String) : MigrationException(message)
    class AlreadyApplied(fileName: String) :
        MigrationException("Migration `$fileName` already applied")

    class IntegrityCheckFailed(forFile: String) :
        MigrationException("`$forFile` file integrity check failed. The file has been changed")

    class ApplyingMigrationFailed(forFile: String) :
        MigrationException("Unable to process migration: `$forFile`")
}
