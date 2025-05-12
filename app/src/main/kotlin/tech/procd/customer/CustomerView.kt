package tech.procd.customer

import java.util.UUID

data class CustomerView(
    val id: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phoneCode: String,
    val phoneNumber: String,
)
