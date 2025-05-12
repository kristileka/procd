package tech.procd.application.mutexes

import tech.procd.application.koin.getSecret
import tech.procd.mutex.NullMutexService
import tech.procd.mutex.PostgresMutexService
import tech.procd.mutex.sql.PostgresMutexStore
import org.koin.dsl.module

val mutexModule = module {

    single {
        when (getSecret("MUTEX_ADAPTER")) {
            "postgres" -> PostgresMutexService(
                store = PostgresMutexStore(
                    sqlClient = get(),
                ),
            )

            "null" -> NullMutexService

            else -> error("Unsupported mutex adapter")
        }
    }
}
