package tech.procd.application.ktor.plugin

import tech.procd.application.ktor.remoteAddr
import tech.procd.customer.account.auth.AuthException
import io.ktor.server.application.*
import io.ktor.util.*

class IpWhiteListPlugin {
    object Configuration

    fun interceptPipeline(pipeline: ApplicationCallPipeline, whitelist: List<String>) {
        pipeline.intercept(ApplicationCallPipeline.Monitoring) {
            if (call.remoteAddr() !in whitelist) {
                throw AuthException.Restricted
            }
        }
    }

    companion object Plugin :
        BaseApplicationPlugin<ApplicationCallPipeline, Configuration, IpWhiteListPlugin> {

        override val key = AttributeKey<IpWhiteListPlugin>("Whitelist Plugin")
        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): IpWhiteListPlugin = IpWhiteListPlugin()

    }
}
