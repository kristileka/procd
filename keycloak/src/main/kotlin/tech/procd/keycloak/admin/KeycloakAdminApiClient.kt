package tech.procd.keycloak.admin

import tech.procd.keycloak.*
import tech.procd.keycloak.toKeycloakRepresentation
import tech.procd.keycloak.userAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.GroupsResource
import org.keycloak.admin.client.resource.RolesResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import java.util.*

@Suppress("TooManyFunctions")
interface KeycloakAdminApiClient {

    suspend fun createAccount(
        account: KeycloakUserAccount,
        withCredentials: KeycloakUserAccount.HashedPassword
    ): UUID

    suspend fun updateAccount(account: KeycloakUserAccount)
    suspend fun changePassword(
        realm: String,
        id: UUID,
        withCredentials: KeycloakUserAccount.HashedPassword
    )

    suspend fun promote(realm: String, id: UUID, group: String)
    suspend fun demote(realm: String, id: UUID, group: String)
    suspend fun findUser(realm: String, id: UUID): KeycloakUserAccount?
    suspend fun findUser(realm: String, email: String): KeycloakUserAccount?
    suspend fun logout(realm: String, id: UUID)

    suspend fun addRole(realm: String, name: String)
    suspend fun attachRoles(realm: String, roles: List<String>, group: String)
    suspend fun deleteRole(realm: String, name: String)
    suspend fun addGroup(realm: String, name: String, withRoles: List<String>)
    suspend fun deleteGroup(realm: String, name: String, moveUsersTo: String)
}

@Suppress("TooManyFunctions")
class DefaultKeycloakAdminApiClient(
    private val clientPool: KeycloakAdminHttpClient
) : KeycloakAdminApiClient {

    override suspend fun createAccount(
        account: KeycloakUserAccount,
        withCredentials: KeycloakUserAccount.HashedPassword
    ): UUID = clientPool.withClient {
        val usersResource = users(account.realmId)

        usersResource.findOne(account.email)?.let {
            throw KeycloakExceptions.UserAlreadyExists(account.realmId, account.email)
        }

        usersResource.add(
            account.userRepresentation().apply {
                credentials = listOf(withCredentials.toKeycloakRepresentation())
                isEnabled = true
                isEmailVerified = true
            },
        )
    }

    override suspend fun updateAccount(account: KeycloakUserAccount) = clientPool.withClient {
        if (account.id == null) {
            error("Id must be specified for update action")
        }

        executeUserUpdate(account.realmId, account.id) { _ ->
            account
        }
    }

    override suspend fun promote(realm: String, id: UUID, group: String) =
        users(realm).findOne(id)?.addGroup(matchGroup(realm, group))
            ?: throw KeycloakExceptions.UserNotExists(realm, id)

    override suspend fun demote(realm: String, id: UUID, group: String) =
        users(realm).findOne(id)?.removeGroup(matchGroup(realm, group))
            ?: throw KeycloakExceptions.UserNotExists(realm, id)

    override suspend fun findUser(realm: String, id: UUID): KeycloakUserAccount? = clientPool
        .withClient {
            users(realm)
                .findOne(id)
                ?.toRepresentation()
                ?.userAccount(realm)
        }

    override suspend fun findUser(realm: String, email: String): KeycloakUserAccount? = clientPool
        .withClient {
            users(realm).findOne(email)
        }

    override suspend fun logout(realm: String, id: UUID) {
        clientPool.withClient { adminClient ->
            val activeSessions = adminClient.realm(realm)
                .users()
                .get(id.toString())
                ?.userSessions?.mapNotNull { it.id }
                ?: listOf()

            activeSessions.forEach {
                try {
                    adminClient.realm(realm).deleteSession(it, true)
                } catch (cause: Exception) {
                    /** Not interests */
                }
            }
        }
    }

    override suspend fun addRole(realm: String, name: String) {
        roles(realm).create(name)
    }

    override suspend fun deleteRole(realm: String, name: String) {
        roles(realm).delete(name)
    }

    override suspend fun deleteGroup(realm: String, name: String, moveUsersTo: String): Unit =
        withContext(Dispatchers.IO) {
            val memberList = groups(realm).delete(name)

            memberList
                .map {
                    async {
                        promote(realm, it, moveUsersTo)
                    }
                }
                .awaitAll()
        }

    override suspend fun addGroup(realm: String, name: String, withRoles: List<String>) {
        groups(realm).create(name)

        attachRoles(
            realm = realm,
            roles = withRoles,
            group = name,
        )
    }

    override suspend fun attachRoles(realm: String, roles: List<String>, group: String) =
        withContext(Dispatchers.IO) {
            val groupRepresentation = groups(realm).load(group)
                ?: throw KeycloakExceptions.GroupNotExist(realm, group)

            val rolesResource = roles(realm)

            val rolesCollection = roles
                .map {
                    async {
                        rolesResource.find(it).toRepresentation()
                    }
                }.awaitAll()

            groups(realm).resource
                .group(groupRepresentation.id)
                .roles()
                .realmLevel()
                .add(rolesCollection)
        }

    override suspend fun changePassword(
        realm: String,
        id: UUID,
        withCredentials: KeycloakUserAccount.HashedPassword
    ) {
        clientPool.withClient {
            users(realm).findOne(id)?.let { resource ->
                val userRepresentation = resource.toRepresentation()

                userRepresentation.credentials = listOf(
                    withCredentials.toKeycloakRepresentation(),
                )

                resource.update(userRepresentation)
            }
        }

        logout(realm, id)
    }

    /**
     * Add user to group.
     */
    private fun UserResource.addGroup(keycloakGroup: String) {
        joinGroup(keycloakGroup)
    }

    /**
     * Remove user from group.
     */
    private fun UserResource.removeGroup(keycloakGroup: String) {
        leaveGroup(keycloakGroup)
    }

    private fun WrappedGroupResource.create(name: String) {
        try {
            resource.add(
                GroupRepresentation().apply {
                    this.path = "/"
                    this.name = name
                },
            )
        } catch (cause: Exception) {
            /** Not interests */
        }
    }

    private fun WrappedRolesResource.find(name: String) = resource.get(name)

    private fun WrappedRolesResource.create(name: String) = try {
        resource.create(
            RoleRepresentation().apply {
                this.name = name
            },
        )
    } catch (cause: Exception) {
        /** Not interests */
    }

    private fun WrappedRolesResource.delete(name: String) {
        try {
            resource.deleteRole(name)
        } catch (cause: Exception) {
            /** Not interests */
        }
    }

    private suspend fun WrappedGroupResource.delete(name: String): List<UUID> {
        val groupRepresentation = load(name) ?: return listOf()
        val group = groups(context).resource.group(groupRepresentation.id)

        val members = group.members()
        group.remove()

        return members.map { UUID.fromString(it.id) }
    }

    private suspend fun WrappedGroupResource.load(name: String): GroupRepresentation? =
        clientPool.withClient {
            groups(context)
                .resource.groups(name, 0, 1)
                .firstOrNull()
        }

    /**
     * Getting a resource for working with users in the context of the current realm.
     * Since the user resource does not contain a link to the realm, we will create a proxy class and wrap the
     * users resource in it.
     */
    private suspend fun users(inRealm: String) = withContext(Dispatchers.IO) {
        WrappedUsersResource(
            resource = realm(inRealm).users(),
            context = inRealm,
        )
    }

    /**
     * Getting a resource for working with roles in the context of the current realm.
     * Since the roles resource does not contain a link to the realm, we will create a proxy class
     * and wrap the users resource in it.
     */
    private suspend fun roles(inRealm: String) = withContext(Dispatchers.IO) {
        WrappedRolesResource(
            resource = realm(inRealm).roles(),
            context = inRealm,
        )
    }

    /**
     * Getting a resource for working with groups in the context of the current realm.
     * Since the roles resource does not contain a link to the realm, we will create a proxy class
     * and wrap the users resource in it.
     */
    private suspend fun groups(inRealm: String) = withContext(Dispatchers.IO) {
        WrappedGroupResource(
            resource = realm(inRealm).groups(),
            context = inRealm,
        )
    }

    /**
     * Search for a user by Email.
     */
    private suspend fun WrappedUsersResource.findOne(email: String) = withContext(Dispatchers.IO) {
        resource.search(email)
            .firstOrNull()
            ?.userAccount(context)
    }

    /**
     * Search for a user by ID.
     */
    private suspend fun WrappedUsersResource.findOne(id: UUID) = withContext(Dispatchers.IO) {
        resource.get(id.toString())
    }

    /**
     * Adding a new user.
     */
    private suspend fun WrappedUsersResource.add(user: UserRepresentation) =
        withContext(Dispatchers.IO) {
            val response = resource.create(user)

            UUID.fromString(
                CreatedResponseUtil.getCreatedId(response),
            )
        }

    /**
     * Update existing user.
     */
    private suspend fun UserResource.save(user: UserRepresentation) = withContext(Dispatchers.IO) {
        update(user)
    }

    /**
     * Perform update user request.
     *
     * @throws [KeycloakExceptions.UserNotExists]
     * @throws [KeycloakExceptions.OperationFailed]
     */
    private suspend fun executeUserUpdate(
        realm: String,
        userId: UUID,
        mutator: (KeycloakUserAccount) -> KeycloakUserAccount
    ) {
        val usersResource = users(realm)
        val userResource = usersResource.findOne(userId)
            ?: throw KeycloakExceptions.UserNotExists(realm, userId)

        userResource.save(
            mutator(
                userResource.toRepresentation().userAccount(realm),
            ).userRepresentation(),
        )
    }

    /**
     * Get the group ID on the Keycloak side
     */
    private suspend fun matchGroup(id: String, group: String): String {
        return groupsList(id)[group] ?: error("Incorrect group: $group")
    }

    /**
     * Gets a map for matching user groups.
     *
     * id -> name
     * name -> id
     */
    private suspend fun groupsList(id: String): Map<String, String> = withContext(Dispatchers.IO) {
        realm(id).groups().groups().associate {
            it.id to it.name
            it.name to it.id
        }
    }

    /**
     * Getting the resource of the current realm.
     */
    private suspend fun realm(id: String) = clientPool.withClient { it.realm(id) }

    private data class WrappedUsersResource(
        val resource: UsersResource,
        val context: String
    )

    private data class WrappedRolesResource(
        val resource: RolesResource,
        val context: String
    )

    private data class WrappedGroupResource(
        val resource: GroupsResource,
        val context: String
    )
}
