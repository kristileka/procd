package tech.procd.keycloak

import java.util.UUID

sealed class KeycloakExceptions(message: String, cause: Exception? = null) :
    Exception(message, cause) {

    class UserAlreadyExists(realm: String, email: String) : KeycloakExceptions(
        "User with email `$email` already registered in `$realm` realm",
    )

    class UserNotExists(realm: String, id: UUID) : KeycloakExceptions(
        "User with id `$id` not found in `$realm` realm",
    )

    class OperationFailed(realm: String, error: Exception) : KeycloakExceptions(
        "Keycloak api operation failed for realm `$realm`: ${error.message}",
        error,
    )

    class EntityAlreadyExists(message: String, realm: String) :
        KeycloakExceptions("$message in realm `$realm`")

    class UnableToGetToken(reason: String) :
        KeycloakExceptions("Unable to obtain JWT token: $reason")

    object AccountDisabled : KeycloakExceptions("Account was disabled")

    class GroupNotExist(groupName: String, realm: String) :
        KeycloakExceptions("Unable to find `$groupName` in `$realm`")

    class UnableToFindClient(clientName: String, realm: String) :
        KeycloakExceptions("Unable to find client `$clientName` identifier for `$realm` realm")
}
