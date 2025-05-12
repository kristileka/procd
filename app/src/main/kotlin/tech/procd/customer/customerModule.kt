package tech.procd.customer

import tech.procd.customer.account.BCryptPasswordEncoder
import tech.procd.customer.account.PasswordEncoder
import tech.procd.application.koin.getSecret
import tech.procd.customer.account.AccountStore
import tech.procd.customer.account.auth.AuthProvider
import tech.procd.customer.account.auth.AuthService
import tech.procd.customer.account.auth.KeycloakAuthService
import tech.procd.customer.account.auth.TokenExtractor
import org.koin.dsl.module

val customerModule = module {
    single {
        AccountStore(
            sqlClient = get(),
        )
    }

    single<AuthService> {
        KeycloakAuthService(
            configuration = get(),
            extractor = get(),
        )
    }

    single {
        TokenExtractor(
            accountStore = get(),
        )
    }

    single {
        AuthProvider(
            keycloakClient = get(),
            keycloakAdminClient = get(),
            tokenExtractor = get(),
            accountStore = get(),
        )
    }

    single<PasswordEncoder> {
        BCryptPasswordEncoder
    }

    single {
        CustomerStore(
            sqlClient = get(),
            keycloakConfiguration = get(),
            keycloakAdminApiClient = get(),
            companyProvider = get()
        )
    }

    single {
        CustomerProvider(
            sqlClient = get(),
            realm = getSecret("AUTH_KEYCLOAK_REALM"),
            keycloakAdminClient = get(),
            keycloakClient = get(),
            customerStore = get(),
            accountStore = get(),
            mutexService = get(),
            passwordEncoder = get()
        )
    }
}
