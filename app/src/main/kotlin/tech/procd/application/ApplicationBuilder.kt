package tech.procd.application

import tech.procd.application.koin.buildKoin
import tech.procd.application.ktor.launchKtorEntryPoint
import tech.procd.infrastructure.db.Migrator
//import tech.procd.inrastructure.keycloak.KeycloakMigrator
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.koin.core.Koin
import org.koin.core.module.Module
import kotlin.system.exitProcess

object ApplicationBuilder {
    private val logger = KotlinLogging.logger {}
    private val koinModules: MutableList<Module> = mutableListOf(
        applicationModule,
    )

    fun withModules(vararg modules: Module): ApplicationBuilder {
        koinModules.addAll(modules.asList())
        return this
    }

    fun run() {
        val koin = buildKoin(koinModules)
        val environment: Environment = koin.get()
        val entryPoint: EntryPoint = koin.get()

        logger.info(
            "Launching the application in `{}` mode (with `{}` entrypoint(s)).",
            environment,
            entryPoint,
        )

        launchEntryPoint(koin, entryPoint)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun launchEntryPoint(koin: Koin, entryPoint: EntryPoint) = when (entryPoint) {
        EntryPoint.ALL -> {
            koin.executeMigrations()
            koin.launchKtorEntryPoint()
        }

        EntryPoint.KTOR -> {
            koin.launchKtorEntryPoint()
        }

        else -> runBlocking {
            when (entryPoint) {
                EntryPoint.DATABASE_MIGRATION -> koin.get<Migrator>().migrate()
//                EntryPoint.KEYCLOAK_MIGRATION -> koin.get<KeycloakMigrator>().migrate()
                else -> Unit
            }

            exitProcess(0)
        }
    }

    private fun Koin.executeMigrations() {
        get<Migrator>().migrate()
//        get<KeycloakMigrator>().migrate()
    }
}
