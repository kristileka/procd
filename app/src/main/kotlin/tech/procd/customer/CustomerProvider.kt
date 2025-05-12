package tech.procd.customer

import tech.procd.api.general.customer.registration.request.CustomerRegistrationRequest
import tech.procd.customer.account.AccountException
import tech.procd.customer.account.AccountStore
import tech.procd.common.CommonApiException
import tech.procd.customer.account.Account
import tech.procd.customer.account.PasswordEncoder
import tech.procd.keycloak.KeycloakExceptions
import tech.procd.keycloak.KeycloakUserAccount
import tech.procd.keycloak.admin.KeycloakAdminApiClient
import tech.procd.keycloak.api.KeycloakClient
import tech.procd.mutex.MutexService
import tech.procd.persistence.postgres.transaction
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.SqlClient
import mu.KotlinLogging
import java.time.LocalDateTime

@Suppress("LongParameterList")
class CustomerProvider(
    private val sqlClient: PgPool,
    private val realm: String,
    private val keycloakAdminClient: KeycloakAdminApiClient,
    private val keycloakClient: KeycloakClient,
    private val customerStore: CustomerStore,
    private val accountStore: AccountStore,
    private val mutexService: MutexService,
    private val passwordEncoder: PasswordEncoder,
) {
    companion object {

        val internalGroupRelation = mapOf(
            Account.Group.CUSTOMER to "customer",
        )

        val externalGroupRelation = internalGroupRelation.entries.associate { it.value to it.key }

        private const val DEFAULT_LOCK_TIMEOUT = 30L

        private val logger = KotlinLogging.logger {}

    }

    suspend fun register(
        request: CustomerRegistrationRequest,
        assignTo: List<Account.Group>,
    ): Customer.Id = try {
        val hashedPassword = passwordEncoder.encode(request.password)

        val id = keycloakAdminClient.createAccount(
            account = KeycloakUserAccount(
                realmId = realm,
                email = request.email,
                groups = assignTo.map {
                    internalGroupRelation[it] ?: error("Unable to find relation for `${it.name}`")
                },
                fullName = KeycloakUserAccount.FullName(
                    firstName = request.firstName,
                    lastName = request.lastName,
                ),
            ),
            withCredentials = KeycloakUserAccount.HashedPassword(hashedPassword),
        ).let { Account.Id(it) }

        customerStore.add(
            id = id,
            realm = Account.Realm(realm),
            request = request,
            assignTo = assignTo,
        )
    } catch (cause: CommonApiException) {
        throw cause
    } catch (cause: Exception) {
        cause.printStackTrace()
        @Suppress("InstanceOfCheckForException")
        if (cause is KeycloakExceptions.UserAlreadyExists) {
            throw AccountException.AlreadyRegistered()
        }

        throw AccountException.OperationFailed(cause)
    }

    /**
     * @throws [CustomerException.NotFound]
     */
    @Suppress("Unused")
    suspend fun <T> load(id: Customer.Id, code: suspend (Customer) -> T): T = mutexService.acquire(
        id = id.value,
        withLifeTime = LocalDateTime.now().plusSeconds(DEFAULT_LOCK_TIMEOUT),
    ) {
        val customer = customerStore.load(id) ?: throw CustomerException.NotFound(id)
        val events: MutableList<CustomerEvent> = mutableListOf()

        val result = sqlClient.transaction {
            try {
                val result = code(customer)

                events.addAll(
                    customerStore.flush(customer, this),
                )

                result
            } catch (cause: Exception) {
                cause.printStackTrace()

                throw cause
            }
        }

        result
    }

    /**
     * @throws [CustomerException.NotFound]
     */
    @Suppress("Unused")
    suspend fun <T> loadWithContext(
        id: Customer.Id,
        code: suspend (Customer, SqlClient) -> T
    ): T = mutexService.acquire(
        id = id.value,
        withLifeTime = LocalDateTime.now().plusSeconds(DEFAULT_LOCK_TIMEOUT),
    ) {
        try {
            val customer = customerStore.load(id) ?: throw CustomerException.NotFound(id)
            val events: MutableList<CustomerEvent> = mutableListOf()

            val result = sqlClient.transaction {
                val result = code(customer, this)

                events.addAll(
                    customerStore.flush(customer, this),
                )

                result
            }

            result
        } catch (cause: CustomerException) {
            throw cause
        } catch (cause: Exception) {
            logger.error("Unable to save customer `{}` aggregate: {}", id.value, cause.message)

            throw cause
        }
    }

    @Suppress("Unused")
    suspend fun customerExist(email: String): Boolean = accountStore.find(email) != null

    @Suppress("Unused")
    suspend fun customerExist(id: Customer.Id): Boolean = customerStore.load(id) != null

    @Suppress("Unused")
    suspend fun validatePassword(account: Account, password: String): Boolean {
        return try {
            keycloakClient.token(
                account.email,
                password,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

}
