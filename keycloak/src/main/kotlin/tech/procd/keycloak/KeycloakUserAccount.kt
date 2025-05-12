package tech.procd.keycloak

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import java.util.UUID

data class KeycloakUserAccount(
    val id: UUID? = null,
    val realmId: String,
    val email: String,
    val groups: List<String>,
    val roles: List<String> = listOf(),
    val fullName: FullName,
) {
    data class HashedPassword(
        val hash: String,
        val algorithm: String = "bcrypt",
        val iterations: Int = 10,
    )

    data class FullName(
        val firstName: String,
        val lastName: String
    ) {
        override fun toString(): String = "$firstName $lastName"
    }


    class Group(
        private val value: String
    ) {
        /**
         * In Keycloak, groups are stored as strings with leading slashes.
         */
        fun toApplicationRepresentation(): String = value.replace("/", "")

        override fun toString(): String = value
    }
}

private const val DEFAULT_PRIORITY = 20

/**
 * Converting the internal representation of customer credentials to Keycloak representation.
 */
internal fun KeycloakUserAccount.HashedPassword.toKeycloakRepresentation(): CredentialRepresentation {
    val credential = CredentialRepresentation()

    credential.id = UUID.randomUUID().toString()
    credential.type = CredentialRepresentation.PASSWORD

    credential.priority = DEFAULT_PRIORITY

    credential.secretData = JsonObject(
        mapOf("value" to JsonPrimitive(hash)),
    ).toString()

    credential.credentialData = JsonObject(
        mapOf(
            "hashIterations" to JsonPrimitive(iterations),
            "algorithm" to JsonPrimitive(algorithm),
        ),
    ).toString()

    return credential
}

/**
 * Converting the user representation into a Keycloak into the internal representation of the application
 */
internal fun UserRepresentation.userAccount(realm: String): KeycloakUserAccount =
    KeycloakUserAccount(
        id = UUID.fromString(id),
        email = email,
        fullName = KeycloakUserAccount.FullName(
            firstName = firstName,
            lastName = lastName,
        ),
        groups = groupCollection(),
        roles = roleCollection(realm),
        realmId = realm,
    )

/**
 * Getting the groups a user is in.
 */
internal fun UserRepresentation.groupCollection(): List<String> =
    (groups?.filterNotNull() ?: listOf())
        .map { KeycloakUserAccount.Group(it).toApplicationRepresentation() }

/**
 * Getting a list of roles that are assigned to a user
 */
internal fun UserRepresentation.roleCollection(forRealm: String): List<String> =
    clientRoles?.get(forRealm)?.filterNotNull() ?: listOf()

/**
 * Convert internal user representation to Keycloak representation.
 */
internal fun KeycloakUserAccount.userRepresentation(): UserRepresentation =
    UserRepresentation().apply {
        id = this@userRepresentation.id.toString()
        email = this@userRepresentation.email
        username = this@userRepresentation.email
        firstName = this@userRepresentation.fullName.firstName
        lastName = this@userRepresentation.fullName.lastName
        groups = this@userRepresentation.groups
        isEnabled = true
    }
