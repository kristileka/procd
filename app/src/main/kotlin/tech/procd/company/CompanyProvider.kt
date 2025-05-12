package tech.procd.company

import io.vertx.pgclient.PgPool
import mu.KotlinLogging
import tech.procd.api.general.company.request.CompanyRegistrationRequest
import tech.procd.common.CommonApiException
import tech.procd.mutex.MutexService
import tech.procd.persistence.postgres.transaction
import java.time.LocalDateTime

@Suppress("LongParameterList")
class CompanyProvider(
    private val sqlClient: PgPool,
    private val mutexService: MutexService,
    private val companyStore: CompanyStore
) {
    companion object {
        private val logger = KotlinLogging.logger {}
        private const val DEFAULT_LOCK_TIMEOUT = 30L

    }

    suspend fun create(
        request: CompanyRegistrationRequest,
    ): Company.Id = try {
        companyStore.add(
            request = request,
        )

    } catch (cause: CommonApiException) {
        throw cause
    }

    /**
     * @throws [CompanyException.NotFound]
     */
    @Suppress("Unused")
    suspend fun <T> load(id: Company.Id, code: suspend (Company) -> T): T = mutexService.acquire(
        id = id.value,
        withLifeTime = LocalDateTime.now().plusSeconds(DEFAULT_LOCK_TIMEOUT),
    ) {
        val company = companyStore.load(id) ?: throw CompanyException.NotFound(id)
        val events: MutableList<CompanyEvent> = mutableListOf()

        val result = sqlClient.transaction {
            try {
                val result = code(company)

                events.addAll(
                    companyStore.flush(company, this),
                )

                result
            } catch (cause: Exception) {
                cause.printStackTrace()

                throw cause
            }
        }

        result
    }

    suspend fun loadCompanies(ids: List<Company.Id>): Map<Company.Id, Company> =
        companyStore.loadCompanies(ids).associateBy {
            it.id
        }
}
