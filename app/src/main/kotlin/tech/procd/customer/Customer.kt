package tech.procd.customer

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import tech.procd.common.CommonApiException
import tech.procd.customer.account.Account
import org.koin.core.component.KoinComponent
import tech.procd.company.Company
import java.time.LocalDateTime
import java.util.*

@Suppress("LongParameterList", "TooManyFunctions", "MaxLineLength")
class Customer(
    val id: Id,
    val accountId: Account.Id,
    val accountInfo: AccountDetails,
    val profileInfo: ProfileDetails,
    private var companyLoader: suspend () -> CompanyCollection,
) {

    private companion object : KoinComponent {
        private var events: MutableList<CustomerEvent> = mutableListOf()
    }

    private lateinit var _companyCollection: CompanyCollection

    suspend fun companies(): CompanyCollection {
        if (!this::_companyCollection.isInitialized) {
            _companyCollection = companyLoader()
        }

        return _companyCollection
    }


    data class Id(@JsonValue val value: UUID) {
        companion object {
            fun create(accountId: Account.Id, realm: Account.Realm) = Id(
                UUID.nameUUIDFromBytes("$realm:${accountId.value}".toByteArray()),
            )

            @JvmStatic
            @JsonCreator
            fun create(id: UUID) = Id(id)
        }

        override fun toString(): String = value.toString()
    }

    class AccountDetails(
        private val customerId: Id,
        private val accountId: Account.Id,
        val realm: Account.Realm,
        private var groupCollection: MutableList<Account.Group>,
        private val roleCollection: MutableList<String>,
    ) {
        fun groups(): List<Account.Group> = groupCollection
        fun roles(): List<String> = roleCollection
    }

    class ProfileDetails(
        private val customerId: Id,
        private val accountId: Account.Id,
        val email: String,
        private var phoneNumber: PhoneNumber,
        private var fullName: FullName,
    ) {
        fun phone() = phoneNumber.copy()
        fun fullName() = fullName.copy()

        fun changePhoneNumber(newPhoneNumber: PhoneNumber) {
            if (phoneNumber.toString() != newPhoneNumber.toString()) {
                events.add(
                    CustomerEvent.PhoneNumberChanged(
                        id = customerId,
                        accountId = accountId,
                        previous = phoneNumber.copy(),
                        new = newPhoneNumber,
                    ),
                )

                phoneNumber = newPhoneNumber
            }
        }

        fun rename(newFullName: FullName) {
            if (fullName != newFullName) {
                events.add(
                    CustomerEvent.FullNameChanged(
                        id = customerId,
                        accountId = accountId,
                        previous = fullName.copy(),
                        new = newFullName,
                    ),
                )

                fullName = newFullName
            }
        }


        data class FullName(
            val firstName: String,
            val lastName: String,
        ) {
            override fun toString() = "$firstName $lastName"
        }

        data class PhoneNumber(
            val code: String,
            val number: String,
        ) {
            override fun toString(): String = "+${code}${number}"
        }
    }

    class CompanyCollection(
        private val customerId: Id,
        private val collection: MutableMap<Company.Id, Company>
    ) {
        fun add(company: Company) {
            collection[company.id] = company

            events.add(
                CustomerEvent.CompanyAdded(
                    id = customerId,
                    company = company
                ),
            )
        }

        fun assign(companyId: Company.Id) {
            events.add(
                CustomerEvent.CompanyAssigned(
                    id = customerId,
                    companyId = companyId
                ),
            )
        }

        fun list() = collection.map { it.value }

        fun get(id: Company.Id): Company? = collection[id]
    }

    /** Called from the infrastructure layer. */
    @Suppress("UnusedPrivateMember", "Unused")
    private fun clearEvents() {
        events = mutableListOf()
    }
}
