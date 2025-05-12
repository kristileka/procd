package tech.procd.keycloak.api

import com.fasterxml.jackson.annotation.JsonProperty

data class KeycloakErrorResponse(
    val error: String,

    @JsonProperty("error_description")
    val errorDescription: String?
)
