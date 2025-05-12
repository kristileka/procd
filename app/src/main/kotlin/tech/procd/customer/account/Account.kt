package tech.procd.customer.account

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import tech.procd.customer.Customer
import io.ktor.server.auth.*
import java.time.LocalDateTime
import java.util.*

data class Account(
    val id: Id,
    val realmId: Realm,
    val email: String,
    val assignedRoles: List<String> = listOf(),
    val assignedGroups: List<Group>,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastAuthorizedAt: LocalDateTime? = null,
) : Principal {
    val customerId: Customer.Id get() = Customer.Id.create(id, realmId)

    data class Id(@JsonValue val value: UUID) {
        companion object {
            @JvmStatic
            @JsonCreator
            fun create(id: UUID) = Id(id)
        }
    }


    data class Realm(@JsonValue val value: String) {
        companion object {
            @JvmStatic
            @JsonCreator
            fun create(realm: String) = Realm(realm)
        }
    }

    enum class Group {
        CUSTOMER,
    }
}
