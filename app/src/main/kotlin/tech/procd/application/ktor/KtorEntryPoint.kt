package tech.procd.application.ktor

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.core.Koin

private const val DEFAULT_LISTEN_HOST = "0.0.0.0"
private const val DEFAULT_LISTEN_PORT = "7369"

internal fun Koin.launchKtorEntryPoint() {

    embeddedServer(
        Netty,
        environment = applicationEngineEnvironment {
            log = get()

            module(Application::ktorModule)

            connector {
                host = getProperty("KTOR_HOST", DEFAULT_LISTEN_HOST)
                port = getProperty("KTOR_PORT", DEFAULT_LISTEN_PORT).toInt()
            }
        },
    ).start(wait = true)
}
