package tech.procd.api.general.customer.registration

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import tech.procd.api.general.customer.registration.request.CustomerRegistrationRequest
import tech.procd.api.helpers.validated
import tech.procd.common.CommonApiException
import tech.procd.common.CommonApiResponse
import tech.procd.customer.CustomerProvider
import tech.procd.customer.account.Account
import tech.procd.customer.account.AccountException

fun Route.customerRegistrationRoutes() {
    val customerProvider: CustomerProvider by inject()

    post("/profiles") {
        val request: CustomerRegistrationRequest = call.validated()

        if (request.password != request.passwordRepeat) {
            throw CommonApiException.IncorrectRequestParameters.create(
                "repeatPassword", "passwords do not match",
            )
        }

        try {
            call.respond(
                HttpStatusCode.OK,
                CommonApiResponse(
                    data = mapOf(
                        "id" to customerProvider.register(
                            request = request,
                            assignTo = listOf(Account.Group.CUSTOMER),
                        ).value,
                    ),
                ),
            )
        } catch (cause: AccountException.AlreadyRegistered) {
            throw CommonApiException.IncorrectRequestParameters.create(
                "email",
                "user already registered",
            )
        }
    }
}
