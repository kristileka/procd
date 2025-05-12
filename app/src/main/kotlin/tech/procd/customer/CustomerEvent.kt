package tech.procd.customer

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import tech.procd.common.Event
import tech.procd.company.Company
import tech.procd.customer.account.Account
import java.time.LocalDateTime

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed class CustomerEvent : Event {
    abstract val occurredAt: LocalDateTime

    @JsonTypeName("Customer.FullNameChanged")
    data class FullNameChanged(
        val id: Customer.Id,
        val accountId: Account.Id,
        val previous: Customer.ProfileDetails.FullName,
        val new: Customer.ProfileDetails.FullName,
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : CustomerEvent()

    @JsonTypeName("Customer.PhoneNumberChanged")
    data class PhoneNumberChanged(
        val id: Customer.Id,
        val accountId: Account.Id,
        val previous: Customer.ProfileDetails.PhoneNumber,
        val new: Customer.ProfileDetails.PhoneNumber,
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : CustomerEvent()

    @JsonTypeName("Customer.CompanyAdded")
    data class CompanyAdded(
        val id: Customer.Id,
        val company: Company,
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : CustomerEvent()

    @JsonTypeName("Customer.CompanyAssigned")
    data class CompanyAssigned(
        val id: Customer.Id,
        val companyId: Company.Id,
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : CustomerEvent()
}
