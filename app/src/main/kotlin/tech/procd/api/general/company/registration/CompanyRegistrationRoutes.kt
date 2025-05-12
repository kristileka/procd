package tech.procd.api.general.company.registration

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import tech.procd.api.general.company.request.CompanyRegistrationRequest
import tech.procd.api.general.customer.registration.request.CustomerRegistrationRequest
import tech.procd.api.helpers.validated
import tech.procd.application.ktor.userAccount
import tech.procd.common.CommonApiException
import tech.procd.common.CommonApiResponse
import tech.procd.company.CompanyProvider
import tech.procd.customer.Customer
import tech.procd.customer.CustomerProvider
import tech.procd.customer.account.Account
import tech.procd.customer.account.AccountException

fun Route.companyRegistrationRoute() {
    val companyProvider: CompanyProvider by inject()
    val customerProvider: CustomerProvider by inject()

    post("/register") {
        val request: CompanyRegistrationRequest = call.validated()
        val customerId = call.userAccount().customerId


        val companyId = customerProvider.load(customerId) {
            val companyId = companyProvider.create(
                request = request,
            )
            it.companies().assign(companyId)
            companyId
        }

        call.respond(
            HttpStatusCode.OK,
            CommonApiResponse(
                data = mapOf(
                    "id" to companyId.value,
                ),
            )
        )
    }
}
