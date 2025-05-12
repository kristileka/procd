package tech.procd.application

import com.fasterxml.jackson.databind.json.JsonMapper
import tech.procd.application.koin.getBooleanSecret
import tech.procd.application.koin.getListSecret
import tech.procd.application.koin.getSecret
import tech.procd.application.ktor.KtorCorsConfiguration
import tech.procd.infrastructure.db.DatabaseMigrator
import tech.procd.infrastructure.db.Migrator
import tech.procd.application.serializer.JacksonSerializer
import tech.procd.application.serializer.Serializer
import tech.procd.application.serializer.mapper.configure
import tech.procd.keycloak.KeycloakApiClient
import tech.procd.keycloak.KeycloakConfiguration
import tech.procd.keycloak.KeycloakCredentials
import tech.procd.keycloak.admin.DefaultKeycloakAdminApiClient
import tech.procd.keycloak.admin.KeycloakAdminApiClient
import tech.procd.keycloak.admin.KeycloakAdminHttpClient
import tech.procd.keycloak.api.DefaultKeycloakClient
import tech.procd.keycloak.api.KeycloakClient
import tech.procd.persistence.postgres.StorageConfiguration
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.vertx.core.Vertx
import io.vertx.core.net.PemTrustOptions
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.pgclient.SslMode
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlClient
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import tech.procd.infrastructure.keycloak.KeycloakMigrator
import java.time.Duration

private const val DEFAULT_DATABASE_PORT = 5432
private const val DEFAULT_DATABASE_POOL_SIZE = 100

val applicationModule = module {
    single {
        Environment.valueOf(
            getSecret("APPLICATION_STAGE", "development").uppercase(),
        )
    }

    single {
        EntryPoint.valueOf(
            getSecret("APPLICATION_ENTRY_POINT", "all").uppercase(),
        )
    }


    single {
        JsonMapper.builder().build().configure()
    }


    single {
        KtorCorsConfiguration(
            host = getSecret("KTOR_CORS_HOST"),
            schemas = getListSecret("KTOR_CORS_SCHEMAS"),
            subDomains = getListSecret("KTOR_CORS_SUBDOMAINS"),
            allowCredentials = getBooleanSecret("KTOR_CORS_ALLOW_CREDENTIALS"),
            maxAge = getSecret("KTOR_CORS_MAX_AGE", "86400").toLong(),
            allowedMethods = getListSecret("KTOR_CORS_ALLOWED_METHODS"),
            allowedHeaders = getListSecret("KTOR_CORS_ALLOWED_HEADERS"),
        )
    }

    @Suppress("MagicNumber") single {
        HttpClient(CIO) {
            expectSuccess = false

            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 5_000
            }

            engine {
                requestTimeout = 20_000
            }
        }
    }

    single<Serializer> {
        JacksonSerializer(
            objectMapper = get(),
        )
    }

    single {
        StorageConfiguration(
            host = getSecret("DATABASE_HOST", "localhost"),
            port = getSecret("DATABASE_PORT", "$DEFAULT_DATABASE_PORT").toInt(),
            database = getSecret("DATABASE_NAME"),
            username = getSecret("DATABASE_USERNAME"),
            password = getSecret("DATABASE_PASSWORD"),
            requireSsl = getBooleanSecret("DATABASE_SSL_REQUIRE"),
        )
    }

    single<Migrator> {
        DatabaseMigrator(
            configuration = get(),
        )
    }

    single {
        KeycloakMigrator(
            client = get(),
            sqlClient = get(),
        )
    }

    single {
        val configuration = get<StorageConfiguration>()

        val connectionOptions = PgConnectOptions().apply {
            port = configuration.port
            host = configuration.host
            database = configuration.database
            user = configuration.username
            password = configuration.password

            if (configuration.requireSsl) {
                sslMode = SslMode.REQUIRE
                trustOptions = PemTrustOptions()
                    .addCertPath("/usr/local/share/ca-certificates/ca-certificate.crt")
            }
        }

        val poolOptions = PoolOptions().apply {
            maxSize = getSecret(
                "DATABASE_POOL_SIZE",
                "$DEFAULT_DATABASE_POOL_SIZE",
            ).toInt()
        }

        PgPool.pool(Vertx.vertx(), connectionOptions, poolOptions)
    }

    single<SqlClient> {
        get<PgPool>()
    }

    single {
        KeycloakConfiguration(
            activeRealm = getSecret("AUTH_KEYCLOAK_REALM"),
            serverUrl = getSecret("AUTH_KEYCLOAK_SERVER_URL"),
            internalServerUrl = getSecret("AUTH_KEYCLOAK_SERVER_INTERNAL_URL"),
            credentials = KeycloakCredentials(
                username = getSecret("AUTH_KEYCLOAK_USER"),
                password = getSecret("AUTH_KEYCLOAK_PASSWORD"),
            ),
            apiClientInfo = KeycloakApiClient(
                id = getSecret("AUTH_KEYCLOAK_API_CLIENT_ID"),
                secret = getSecret("AUTH_KEYCLOAK_API_CLIENT_SECRET"),
            ),
            adminClientInfo = KeycloakApiClient(
                id = getSecret("AUTH_KEYCLOAK_ADMIN_CLIENT_ID"),
                secret = getSecret("AUTH_KEYCLOAK_ADMIN_CLIENT_SECRET"),
            ),
            logInteractions = getBooleanSecret("AUTH_KEYCLOAK_CLIENT_LOG_INTERACTIONS"),
            maxConnectionAttempts = getSecret(
                "AUTH_KEYCLOAK_MAX_CONNECTION_ATTEMPTS",
                "10",
            ).toInt(),
            defaultRecoveryDelay = getSecret(
                "AUTH_KEYCLOAK_CONNECTION_RECOVERY_DELAY",
                "1000",
            ).toLong(),
            mainRealmName = getSecret("AUTH_KEYCLOAK_MAIN_REALM_NAME", "master"),
        )
    }

    single {
        KeycloakAdminHttpClient(
            configuration = get(),
        )
    }

    single<KeycloakAdminApiClient> {
        DefaultKeycloakAdminApiClient(
            clientPool = get(),
        )
    }

    single<KeycloakClient> {
        DefaultKeycloakClient(
            configuration = get(),
            objectMapper = get(),
        )
    }

    single {
        val binders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            JvmGcMetrics(),
            FileDescriptorMetrics(),
            UptimeMetrics(),
        )

        val registry = PrometheusMeterRegistry(
            object : PrometheusConfig {
                override fun get(key: String): String? = null
                override fun step() =
                    Duration.ofSeconds(getSecret("PROMETHEUS_INTERVAL", "10").toLong())
            },
        )

        binders.forEach {
            it.bindTo(registry)
        }

        registry
    }

    single {
        LoggerFactory.getLogger("tech.procd")
    }
}
