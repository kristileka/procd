package tech.procd.keycloak.api

import org.keycloak.representations.idm.authorization.ResourceRepresentation
import org.keycloak.representations.idm.authorization.ScopeRepresentation
import java.util.*

private const val MAX_RESOURCE_NAME_LENGTH = 250

typealias KeycloakResourceId = UUID

internal fun Resource.toKeycloakRepresentation() = ResourceRepresentation().apply {
    name = title()
    type = this@toKeycloakRepresentation.type
    scopes = this@toKeycloakRepresentation.scopes.map { it.toKeycloakRepresentation() }.toSet()
    attributes = this@toKeycloakRepresentation.attributes.map {
        it.key to listOf(it.value)
    }.toMap()
}

internal fun Scope.toKeycloakRepresentation() = ScopeRepresentation().apply {
    name = value
}

private fun Resource.title(): String {
    if ("$name [$id]".length < MAX_RESOURCE_NAME_LENGTH) {
        return "$name [$id]"
    }

    return id
}
