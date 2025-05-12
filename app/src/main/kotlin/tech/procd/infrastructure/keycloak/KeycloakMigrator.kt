package tech.procd.infrastructure.keycloak

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tech.procd.infrastructure.CommonMigration
import tech.procd.infrastructure.MigrationException
import tech.procd.keycloak.admin.KeycloakAdminApiClient
import io.vertx.pgclient.PgPool
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

class KeycloakMigrator(
    private val client: KeycloakAdminApiClient,
    sqlClient: PgPool,
    historyTableName: String = "infrastructure_migrations_keycloak",
    migrationsDirectory: String = "/migrations/keycloak"
) : CommonMigration(sqlClient, historyTableName, migrationsDirectory) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun migrate() = runBlocking {
        logger.info("Applying Keycloak Migration")

        listMigrations().forEach { entry: MigrationEntry ->

            try {
                handle(entry) {
                    val migrations = entry.parse()

                    migrations.migrations.forEach { instruction ->
                        try {
                            when (instruction) {
                                is Migration.AddRoles -> instruction.list.forEach { role ->
                                    client.addRole(instruction.realm, role)
                                }

                                is Migration.AttachRole -> client.attachRoles(
                                    realm = instruction.realm,
                                    roles = instruction.list,
                                    group = instruction.to,
                                )

                                is Migration.DeleteRole -> client.deleteRole(
                                    instruction.realm,
                                    instruction.role,
                                )

                                is Migration.AddGroup -> {
                                    instruction.with.forEach { role ->
                                        client.addRole(instruction.realm, role)
                                    }

                                    client.addGroup(
                                        realm = instruction.realm,
                                        name = instruction.group,
                                        withRoles = instruction.with,
                                    )
                                }

                                is Migration.GroupDelete -> client.deleteGroup(
                                    realm = instruction.realm,
                                    name = instruction.group,
                                    moveUsersTo = instruction.moveTo,
                                )
                            }
                        } catch (cause: Exception) {
                            throw MigrationException.ApplyingMigrationFailed(entry.fileName)
                        }
                    }
                }
            } catch (cause: MigrationException.AlreadyApplied) {
                /** Not interest **/
            }
        }

        logger.info("Keycloak migration completed")
    }

    private fun MigrationEntry.parse() = yamlObjectMapper.readValue(
        File(filePath),
        Migrations::class.java,
    )
}

private class Migrations(
    val migrations: List<Migration>
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Migration.AddRoles::class, name = "ROLE_ADD"),
    JsonSubTypes.Type(value = Migration.AttachRole::class, name = "ROLE_ATTACH"),
    JsonSubTypes.Type(value = Migration.DeleteRole::class, name = "ROLE_DELETE"),
    JsonSubTypes.Type(value = Migration.AddGroup::class, name = "GROUP_ADD"),
    JsonSubTypes.Type(value = Migration.GroupDelete::class, name = "GROUP_DELETE"),
)
private sealed class Migration {
    enum class Type {
        ROLE_ADD,
        ROLE_ATTACH,
        ROLE_DELETE,
        GROUP_ADD,
        GROUP_DELETE
    }

    abstract val type: Type
    abstract val realm: String

    data class AddRoles(
        override val type: Type = Type.ROLE_ADD,
        override val realm: String,
        val list: List<String>
    ) : Migration()

    data class AttachRole(
        override val type: Type = Type.ROLE_ATTACH,
        override val realm: String,
        val list: List<String>,
        val to: String
    ) : Migration()

    data class DeleteRole(
        override val type: Type = Type.ROLE_DELETE,
        override val realm: String,
        val role: String
    ) : Migration()

    data class AddGroup(
        override val type: Type = Type.GROUP_ADD,
        override val realm: String,
        val group: String,
        val with: List<String>
    ) : Migration()

    data class GroupDelete(
        override val type: Type = Type.GROUP_DELETE,
        override val realm: String,
        val group: String,
        val moveTo: String
    ) : Migration()
}
