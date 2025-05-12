package tech.procd.application.ktor

import tech.procd.application.ktor.plugin.AuthorizationPlugin
import tech.procd.customer.account.Account
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Route.isGranted(
    expectedRole: String,
    build: Route .() -> Unit
): Route {
    val authorizedRoute = createChild(AuthorizedRouteSelector(expectedRole))

    application.plugin(AuthorizationPlugin)
        .interceptPipeline(authorizedRoute, expectedRole)
    authorizedRoute.build()

    return authorizedRoute
}

fun ApplicationCall.userAccount(): Account =
    principal() ?: error("Unexpected behavior: unable to find principal")

private class AuthorizedRouteSelector(private val description: String) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Constant

    override fun toString(): String = "(Authorize $description)"
}
