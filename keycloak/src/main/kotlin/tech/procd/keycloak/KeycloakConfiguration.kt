package tech.procd.keycloak

data class KeycloakConfiguration(
    val activeRealm: String,
    val serverUrl: String,
    val internalServerUrl: String,
    val credentials: KeycloakCredentials,
    val apiClientInfo: KeycloakApiClient,
    val adminClientInfo: KeycloakApiClient,
    val logInteractions: Boolean = false,
    val maxConnectionAttempts: Int = DEFAULT_MAX_CONNECTION_ATTEMPT,
    val defaultRecoveryDelay: Long = DEFAULT_AWAIT_DELAY,
    val mainRealmName: String = DEFAULT_MAIN_REALM
) {
    private companion object {
        private const val DEFAULT_MAIN_REALM = "master"
        private const val DEFAULT_AWAIT_DELAY = 1_000L
        private const val DEFAULT_MAX_CONNECTION_ATTEMPT = 10
    }

    val internalRealmUrl = "$internalServerUrl/realms/$activeRealm"
    val publicRealmUrl = "$serverUrl/realms/$activeRealm"
    val certificateUrl = "$internalRealmUrl/protocol/openid-connect/certs"
    val tokenUrl = "$internalRealmUrl/protocol/openid-connect/token"
    val verifyUrl = "$internalRealmUrl/protocol/openid-connect/token"
    val resourcesUrl = "$internalRealmUrl/authz/protection/resource_set"
    val revokeUrl = "$internalRealmUrl/protocol/openid-connect/revoke"
    val infoUrl = "$internalRealmUrl/protocol/openid-connect/userinfo"
}

data class KeycloakCredentials(
    val username: String,
    val password: String
)

data class KeycloakApiClient(
    val id: String,
    val secret: String?
)
