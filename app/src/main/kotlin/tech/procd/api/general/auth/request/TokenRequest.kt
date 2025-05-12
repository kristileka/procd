package tech.procd.api.general.auth.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

internal data class TokenRequest(
    @field:NotBlank
    @field:Email
    val login: String,

    @field:NotBlank
    @field:Size(min = 5, max = 36)
    val password: String
)
