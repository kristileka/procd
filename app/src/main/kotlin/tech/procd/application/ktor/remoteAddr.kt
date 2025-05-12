package tech.procd.application.ktor

import io.ktor.server.application.*
import io.ktor.server.plugins.*

fun ApplicationCall.remoteAddr() = request.origin.remoteHost.replace("localhost", "127.0.0.1")
