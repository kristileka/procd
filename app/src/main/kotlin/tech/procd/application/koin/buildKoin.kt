package tech.procd.application.koin

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.environmentProperties
import org.koin.fileProperties
import java.nio.file.Files
import java.nio.file.Paths

internal fun buildKoin(modules: List<Module>) = startKoin {
    fileProperties("/application-local.properties")
    environmentProperties()

    modules(modules)
}.koin

fun Scope.getSecret(name: String, defaultValue: String = ""): String = try {
    val environmentValue = System.getenv(name)

    if (environmentValue != null) {
        String(Files.readAllBytes(Paths.get(environmentValue)))
    } else {
        getProperty(name, defaultValue)
    }
} catch (cause: Exception) {
    getProperty(name, defaultValue)
}

fun Scope.getListSecret(propertyName: String): List<String> = getSecret(
    name = propertyName,
    defaultValue = "",
)
    .split(",")
    .filter {
        it.isNotEmpty()
    }
    .distinct()

fun Scope.getBooleanSecret(propertyName: String): Boolean =
    when (getSecret(propertyName)) {
        "true" -> true
        else -> false
    }

