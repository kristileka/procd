package tech.procd.api.external

import tech.procd.customer.account.auth.AuthException
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*


private val authorizationPhase = PipelinePhase("Authorization")
private val challengePhase = PipelinePhase("Challenge")

fun Route.generalImporterRoute() = isGrantedImporter {
    route("/general-importer") {
        post("/import") {
            call.respond("Imported")
        }
    }
}


private fun Route.isGrantedImporter(
    build: Route .() -> Unit
): Route {

    val pipeline = createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    })

    pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, challengePhase)
    pipeline.insertPhaseAfter(challengePhase, authorizationPhase)

    pipeline.intercept(authorizationPhase) {
        val securityHeader = call.request.headers["security-header-x"]
        if (securityHeader != "My super secure header") throw AuthException.NotAllowed()
    }
    build()
    return pipeline
}

