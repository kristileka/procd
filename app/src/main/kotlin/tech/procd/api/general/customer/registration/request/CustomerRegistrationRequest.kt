package tech.procd.api.general.customer.registration.request

import jakarta.validation.constraints.*

@Suppress("MagicNumber")
data class CustomerRegistrationRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 255)
    val firstName: String,

    @field:NotBlank
    @field:Size(min = 2, max = 255)
    val lastName: String,

    @field:NotBlank
    @field:Email
    val email: String,

    @field:Size(min = 6, message = "password must be at least 6 characters long")
    @field:Pattern(
        regexp = ".*[1-9].*",
        message = "password must contain at least one number from 1 to 9",
    )
    @field:Pattern(
        regexp = ".*[a-zA-Z].*",
        message = "password must contain at least one letter",
    )
    val password: String,

    @field:Size(min = 6, message = "password must be at least 6 characters long")
    @field:Pattern(
        regexp = ".*[1-9].*",
        message = "password must contain at least one number from 1 to 9",
    )
    @field:Pattern(
        regexp = ".*[a-zA-Z].*",
        message = "password must contain at least one letter",
    )
    val passwordRepeat: String,

    @field:NotBlank
    @field:Size(min = 1, max = 5)
    val phoneCode: String,

    @field:NotBlank
    @field:Size(min = 1, max = 20)
    val phoneNumber: String,

    )
