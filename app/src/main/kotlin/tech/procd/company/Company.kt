package tech.procd.company

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDateTime
import java.util.*

data class Company(
    val id: Id,
    val subscriptionDetails: SubscriptionDetails,
    val companyDetails: CompanyDetails,
    private var subscriptionLoader: suspend () -> SubscriptionCollection,
) {


    data class Id(@JsonValue val value: UUID) {
        companion object {
            @JvmStatic
            @JsonCreator
            fun create(id: UUID) = Id(id)
            fun next() = Id(UUID.randomUUID())
        }
    }

    class SubscriptionDetails(
        val currentSubscription: SubscriptionType,
        val leftSubscription: Int,
        private val expiration: LocalDateTime
    ) {
        fun isExpired() = expiration.isBefore(LocalDateTime.now())
    }

    data class CompanyDetails(
        val name: String,
    )

    data class SubscriptionCollection(
        val id: Id,
        val subscriptions: List<Subscription>
    ) {
        data class Subscription(
            val date: LocalDateTime,
            val subscriptionType: SubscriptionType,
            val expiration: LocalDateTime
        )
    }

    enum class SubscriptionType {
        FREE, BASIC, PREMIUM
    }


}

