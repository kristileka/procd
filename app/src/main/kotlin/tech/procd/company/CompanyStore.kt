package tech.procd.company

import tech.procd.persistence.postgres.execute
import tech.procd.persistence.postgres.fetchOne
import tech.procd.persistence.postgres.querybuilder.insert
import tech.procd.persistence.postgres.querybuilder.select
import tech.procd.persistence.postgres.transaction
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import tech.procd.api.general.company.request.CompanyRegistrationRequest
import tech.procd.persistence.postgres.fetchAll
import java.lang.reflect.Field
import java.time.LocalDateTime

@Suppress("TooManyFunctions", "LargeClass", "LongParameterList", "MaxLineLength")
class CompanyStore(
    private val sqlClient: PgPool,
) {
    suspend fun add(
        request: CompanyRegistrationRequest,
    ): Company.Id {

        val companyId = Company.Id.next()

        sqlClient.transaction {
            execute(
                insert(
                    to = "company",
                    rows = mapOf(
                        "id" to companyId.value,
                        "name" to request.name,
                        "subscription_type" to request.subscriptionType,
                        "subscription_expiration" to LocalDateTime.now().plusMonths(1),
                        "left_subscription_count" to 1,
                    ),
                ),
            )
        }
        return companyId
    }



    suspend fun load(id: Company.Id): Company? = sqlClient.fetchOne(
        select("customer") {
            where {
                "id" eq id.value
            }
        },
    )?.asCompany()


    /**
     *
     * Retrieve all domain events and save the final state of the aggregate.
     */
    @Suppress("Unused")
    suspend fun flush(company: Company): List<CompanyEvent> = sqlClient.transaction {
        flush(company, this)
    }

    /**
     * Retrieve all domain events and save the final state of the aggregate.
     */
    suspend fun flush(customer: Company, withConnection: SqlClient): List<CompanyEvent> {
        @Suppress("Unchecked_cast") val events =
            customer.eventsReflectionProperty().get(customer) as List<CompanyEvent>

        customer.eraseEvents()

        if (events.isEmpty()) {
            return listOf()
        }

        val customerUpdates: MutableMap<String, Any?> = mutableMapOf()

        events.forEach { event ->
            val changes = withConnection.apply(customer, event)
            customerUpdates.putAll(changes.companyUpdates)
        }

        return events
    }

    /**
     * Depending on the received domain event, we need to correctly save the final state of the user.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun SqlClient.apply(
        company: Company,
        event: CompanyEvent
    ): AggregateChanges {
        val aggregateChanges = AggregateChanges()
        when (event) {
            else -> {}
        }
        return aggregateChanges
    }

    suspend fun loadCompanies(ids: List<Company.Id>) = sqlClient.fetchAll(
        select("company") {
            where {
                "id" anyIn ids.map { it.value }
            }
        }
    ).map { it.asCompany() }

    @Suppress("LongMethod")
    private suspend fun Row.asCompany(): Company {
        val companyId = Company.Id(getUUID("id"))

        return Company(
            id = companyId,
            companyDetails = Company.CompanyDetails(
                getString("name")
            ),
            subscriptionDetails = Company.SubscriptionDetails(
                Company.SubscriptionType.valueOf(getString("subscription_type")),
                expiration = getLocalDateTime("subscription_expiration"),
                leftSubscription = getInteger("left_subscription_count")
            ),
            subscriptionLoader = {
                Company.SubscriptionCollection(
                    companyId,
                    fetchSubscriptionsFor(companyId)
                )
            }
        )
    }

    private suspend fun fetchSubscriptionsFor(companyId: Company.Id) = sqlClient.fetchAll(
        select("subscription") {
            where {
                "company_id" eq companyId.value
            }
        }).map {
        Company.SubscriptionCollection.Subscription(
            date = it.getLocalDateTime("date"),
            expiration = it.getLocalDateTime("expiration"),
            subscriptionType = Company.SubscriptionType.valueOf(it.getString("type"))
        )
    }

    private fun Company.eraseEvents() {
        val reflectionMethod = this::class.java.getDeclaredMethod("clearEvents")
        reflectionMethod.isAccessible = true

        reflectionMethod.invoke(this)
    }

    private fun Company.eventsReflectionProperty(): Field {
        val reflectionProperty = this::class.java.getDeclaredField("events")
        reflectionProperty.isAccessible = true

        return reflectionProperty
    }

    private data class AggregateChanges(
        val companyUpdates: MutableMap<String, Any?> = mutableMapOf(),
    )
}
