package tech.procd.company

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import tech.procd.common.Event
import tech.procd.company.Company
import tech.procd.customer.account.Account
import java.time.LocalDateTime

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed class CompanyEvent : Event {
    abstract val occurredAt: LocalDateTime
}
