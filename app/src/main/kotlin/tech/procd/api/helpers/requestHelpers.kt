package tech.procd.api.helpers

import tech.procd.common.CommonApiException
import tech.procd.customer.Customer
import io.ktor.server.application.*
import io.ktor.server.request.*
import jakarta.validation.Validation
import mu.KotlinLogging
import java.util.*


suspend inline fun <reified T : Any> ApplicationCall.validated(): T = try {
    val validator = Validation.buildDefaultValidatorFactory().validator

    val structure = receive(T::class)
    val violations = validator.validate(structure)

    if (violations.isNotEmpty()) {
        throw CommonApiException.IncorrectRequestParameters(
            violations = violations.associate {
                it.propertyPath.toString() to it.message.toString()
            },
        )
    }

    structure
} catch (cause: CommonApiException.IncorrectRequestParameters) {
    throw cause
} catch (cause: Exception) {
    KotlinLogging.logger {}.debug(cause::message)

    throw CommonApiException.IncorrectRequestParameters.create(
        "body",
        "Unable to parse request JSON: ${cause.cause?.message}",
    )
}

fun <T> ApplicationCall.pathVariable(key: String, parser: (value: String) -> T): T = parameters[key]?.let {
    parser(it)
} ?: throw CommonApiException.IncorrectRequestParameters.create(
    key,
    "Parameter not found in request",

    )


fun ApplicationCall.getCustomerId(key: String = "id"): Customer.Id =
    parameters[key]?.let(UUID::fromString)?.let { Customer.Id.create(it) }
        ?: throw CommonApiException.IncorrectRequestParameters.create(
            key,
            "Parameter not found in request",
        )