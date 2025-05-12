package tech.procd.api.general.auth.request

import jakarta.validation.constraints.NotBlank

internal data class RefreshTokenRequest(
    @field:NotBlank

    val token: String
)

