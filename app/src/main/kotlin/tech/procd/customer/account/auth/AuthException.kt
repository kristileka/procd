package tech.procd.customer.account.auth

sealed class AuthException(message: String) : Exception(message) {
    data object EmailShouldBeVerified : AuthException("You have to verify your email first")
    data object NoPublicKeyFound : AuthException("No public key found")
    data object IncorrectAuthToken : AuthException("Received incorrect auth token")
    data object IpAddressChanged : AuthException("IP address was changed")
    data object CustomerIsSelfExcluded : AuthException("Customer is self excluded")
    class NotAllowed(vararg roles: String) :
        AuthException(
            "The client does\'t have the required roles (${roles.joinToString(", ")}) to access the resource",
        )

    data object Restricted : AuthException("Unable to access to this resource with current IP")
    class Failed(message: String) : AuthException(message)
}
