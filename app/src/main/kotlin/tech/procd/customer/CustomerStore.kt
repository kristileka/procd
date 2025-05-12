package tech.procd.customer

import tech.procd.api.general.customer.registration.request.CustomerRegistrationRequest
import tech.procd.customer.account.Account
import tech.procd.customer.account.AccountStore
import tech.procd.keycloak.KeycloakConfiguration
import tech.procd.keycloak.KeycloakUserAccount
import tech.procd.keycloak.admin.KeycloakAdminApiClient
import tech.procd.persistence.postgres.execute
import tech.procd.persistence.postgres.fetchOne
import tech.procd.persistence.postgres.querybuilder.insert
import tech.procd.persistence.postgres.querybuilder.select
import tech.procd.persistence.postgres.transaction
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import tech.procd.company.Company
import tech.procd.company.CompanyProvider
import tech.procd.persistence.postgres.fetchAll
import java.lang.reflect.Field

@Suppress("TooManyFunctions", "LargeClass", "LongParameterList", "MaxLineLength")
class CustomerStore(
    private val sqlClient: PgPool,
    private val keycloakConfiguration: KeycloakConfiguration,
    private val keycloakAdminApiClient: KeycloakAdminApiClient,
    private val companyProvider: CompanyProvider
) {
    suspend fun add(
        id: Account.Id,
        realm: Account.Realm,
        request: CustomerRegistrationRequest,
        assignTo: List<Account.Group>
    ): Customer.Id {
        val customerId = Customer.Id.create(id, realm)

        sqlClient.transaction {
            AccountStore(this).add(
                Account(
                    id = id,
                    realmId = realm,
                    email = request.email,
                    assignedGroups = assignTo
                ),
            )

            execute(
                insert(
                    to = "customer",
                    rows = mapOf(
                        "id" to customerId.value,
                        "account_id" to id.value,
                        "first_name" to request.firstName,
                        "last_name" to request.lastName,
                        "phone_code" to request.phoneCode.replace("+", ""),
                        "phone_number" to request.phoneNumber,
                    ),
                ),
            )
        }
        return customerId
    }


    suspend fun load(id: Customer.Id): Customer? = sqlClient.fetchOne(
        select("customer") {
            where {
                "id" eq id.value
            }
        },
    )?.asCustomer()


    suspend fun loadView(id: Customer.Id): CustomerView? = sqlClient.fetchOne(
        select("customer", listOf("customer.*", "email")) {
            "account" innerJoin "customer.account_id  = account.id"
            where {
                "customer.id" eq id.value
            }
        },
    )?.asView()

    /**
     *
     * Retrieve all domain events and save the final state of the aggregate.
     */
    @Suppress("Unused")
    suspend fun flush(customer: Customer): List<CustomerEvent> = sqlClient.transaction {
        flush(customer, this)
    }

    /**
     * Retrieve all domain events and save the final state of the aggregate.
     */
    suspend fun flush(customer: Customer, withConnection: SqlClient): List<CustomerEvent> {
        @Suppress("Unchecked_cast") val events =
            customer.eventsReflectionProperty().get(customer) as List<CustomerEvent>

        customer.eraseEvents()

        if (events.isEmpty()) {
            return listOf()
        }

        var keycloakShouldBeUpdated = false
        val customerUpdates: MutableMap<String, Any?> = mutableMapOf()

        events.forEach { event ->
            val changes = withConnection.apply(customer.accountId, event)

            customerUpdates.putAll(changes.customerUpdates)

            if (changes.keycloakShouldBeUpdated) {
                keycloakShouldBeUpdated = true
            }
        }


        if (keycloakShouldBeUpdated) {
            keycloakAdminApiClient.updateAccount(
                customer.toKeycloakAccount(
                    keycloakConfiguration.activeRealm,
                ),
            )
        }
        return events
    }

    /**
     * Depending on the received domain event, we need to correctly save the final state of the user.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun SqlClient.apply(
        accountId: Account.Id, event: CustomerEvent
    ): AggregateChanges {
        val aggregateChanges = AggregateChanges()

        val accountStore = AccountStore(this)
        accountStore.load(accountId)!!
        when (event) {
            is CustomerEvent.FullNameChanged -> {
                aggregateChanges.customerUpdates.putAll(
                    mapOf(
                        "first_name" to event.new.firstName,
                        "last_name" to event.new.lastName,
                    ),
                )

                aggregateChanges.keycloakShouldBeUpdated = true
            }

            is CustomerEvent.PhoneNumberChanged -> {
                aggregateChanges.customerUpdates.putAll(
                    mapOf(
                        "phone_code" to event.new.code.replace("+", ""),
                        "phone_number" to event.new.number,
                    ),
                )
            }

            is CustomerEvent.CompanyAdded -> TODO()
            is CustomerEvent.CompanyAssigned -> sqlClient.execute(
                insert(
                    "customer_company",
                    mapOf(
                        "customer_id" to accountId.value,
                        "company_id" to event.companyId.value,
                        "permission" to "admin",
                    )
                )
            )
        }

        return aggregateChanges
    }


    private fun Row.asAccountId() = Account.Id(getUUID("account_id"))
    private fun Row.asFullName() = Customer.ProfileDetails.FullName(
        firstName = getString("first_name"),
        lastName = getString("last_name"),
    )

    private fun Row.asPhoneNumber() = Customer.ProfileDetails.PhoneNumber(
        code = getString("phone_code").replace("+", ""),
        number = getString("phone_number"),
    )

    private fun Row.asView() = CustomerView(
        id = getUUID("id"),
        email = getString("email"),
        firstName = getString("first_name"),
        lastName = getString("last_name"),
        phoneCode = getString("phone_code"),
        phoneNumber = getString("phone_number"),
    )

    @Suppress("LongMethod")
    private suspend fun Row.asCustomer(): Customer {
        val account = AccountStore(sqlClient).load(asAccountId())!!

        return Customer(
            id = account.customerId,
            accountId = account.id,
            accountInfo = Customer.AccountDetails(
                customerId = account.customerId,
                accountId = account.id,
                realm = account.realmId,
                groupCollection = account.assignedGroups.toMutableList(),
                roleCollection = account.assignedRoles.toMutableList(),
            ),
            profileInfo = Customer.ProfileDetails(
                customerId = account.customerId,
                accountId = account.id,
                email = account.email,
                phoneNumber = asPhoneNumber(),
                fullName = asFullName(),
            ),
            companyLoader = {
                Customer.CompanyCollection(
                    customerId = account.customerId,
                    collection = companyProvider.loadCompanies(fetchCompaniesFor(account.customerId)).toMutableMap()
                )
            }
        )
    }


    private suspend fun fetchCompaniesFor(id: Customer.Id) = sqlClient.fetchAll(
        select("customer_company") {
            where {
                "customer_id" eq id.value
            }
        },
    ).map { Company.Id(it.getUUID("company_id")) }


    private fun Customer.toKeycloakAccount(realm: String) = KeycloakUserAccount(
        id = accountId.value,
        realmId = realm,
        email = profileInfo.email,
        groups = accountInfo
            .groups()
            .map {
                CustomerProvider.internalGroupRelation[it]
                    ?: error("Unable to find relation for `${it.name}`")
            },
        roles = accountInfo.roles(),
        fullName = KeycloakUserAccount.FullName(
            firstName = profileInfo.fullName().firstName,
            lastName = profileInfo.fullName().lastName) ,
    )

    private fun Customer.eraseEvents() {
        val reflectionMethod = this::class.java.getDeclaredMethod("clearEvents")
        reflectionMethod.isAccessible = true

        reflectionMethod.invoke(this)
    }

    private fun Customer.eventsReflectionProperty(): Field {
        val reflectionProperty = this::class.java.getDeclaredField("events")
        reflectionProperty.isAccessible = true

        return reflectionProperty
    }

    private data class AggregateChanges(
        var keycloakShouldBeUpdated: Boolean = false,
        val customerUpdates: MutableMap<String, Any?> = mutableMapOf(),
    )

}