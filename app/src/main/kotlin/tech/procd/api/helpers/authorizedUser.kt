package tech.procd.api.helpers

import tech.procd.customer.account.Account
import io.ktor.server.application.*
import io.ktor.server.auth.*

fun ApplicationCall.authorizedUser() = principal<Account>() ?: error("Unable to find principal")
