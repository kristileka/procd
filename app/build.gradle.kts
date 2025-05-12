plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.10"
    id("com.github.ben-manes.versions")
    id("com.google.cloud.tools.jib")
    kotlin("plugin.serialization")
    id("application")
}

application {
    mainClass.set("tech.procd.MainKt")
}

dependencies {
    implementation(project(":postgres"))
    implementation(project(":mutex"))
    implementation(project(":keycloak"))
    implementation(project(":common"))

    implementation(Dependencies.kotlin("stdlib-jdk8"))
    implementation(Dependencies.kotlinCoroutines("jdk8"))
    implementation(Dependencies.kotlinCoroutines("core"))

    /** Ktor */
    implementation(Dependencies.ktor("server-core"))
    implementation(Dependencies.ktor("serialization-kotlinx-json"))
    implementation(Dependencies.ktor("client-serialization"))
    implementation(Dependencies.ktor("server-status-pages"))
    implementation(Dependencies.ktor("server-auth-jwt"))
    implementation(Dependencies.ktor("server-netty"))
    implementation(Dependencies.ktor("server-content-negotiation"))
    implementation(Dependencies.ktor("server-default-headers"))
    implementation(Dependencies.ktor("server-cors"))
    implementation(Dependencies.ktor("server-forwarded-header"))
    implementation(Dependencies.ktor("server-metrics-micrometer"))
    implementation(Dependencies.ktor("server-forwarded-header"))
    implementation(Dependencies.ktor("server-call-logging"))
    implementation(Dependencies.ktor("serialization-jackson"))
    implementation(Dependencies.ktor("client-cio"))
    implementation(Dependencies.ktor("client-logging"))
    implementation(Dependencies.ktor("client-content-negotiation"))
    implementation(Dependencies.ktor("network-tls-certificates"))
    testImplementation(Dependencies.ktor("server-test-host"))

    /** Koin */
    implementation(Dependencies.koin("core"))
    implementation(Dependencies.koinKtor())

    /** Logging */
    implementation(Dependencies.logback("core"))
    implementation(Dependencies.logback("classic"))
    implementation(Dependencies.kotlinLogging())

    /** Validation */
    implementation(Dependencies.jakarta("el"))
    implementation(Dependencies.hibernateValidator())
    implementation(Dependencies.expressly())

    /** Vertx */
    implementation(Dependencies.scram("common"))
    implementation(Dependencies.scram("client"))
    implementation(Dependencies.vertx("lang-kotlin"))
    implementation(Dependencies.vertx("lang-kotlin-coroutines"))
    implementation(Dependencies.vertx("sql-client"))
    implementation(Dependencies.vertx("pg-client"))

    /** PostgreSQL **/
    implementation(Dependencies.postgres())
    implementation(Dependencies.flyway())

    /** Keycloak */
    implementation(Dependencies.keycloak("authz-client"))

    /** Minio */
    /** Serialization */
    implementation(Dependencies.jackson("dataformat", "dataformat-yaml"))
    implementation(Dependencies.jackson("core", "databind"))
    implementation(Dependencies.jackson("datatype", "datatype-jsr310"))
    implementation(Dependencies.xstream())

    /** Testing */
    testImplementation(kotlin("test"))
    testImplementation(Dependencies.junit("jupiter"))
    testImplementation(Dependencies.testContainers("testcontainers"))
    testImplementation(Dependencies.keycloakTestContainers())
    testImplementation(Dependencies.faker())

    implementation(Dependencies.jbcrypt())
    implementation(Dependencies.micrometer())
}

jib {
    from {
        image = System.getenv("APPLICATION_BASE_IMAGE") ?: "openjdk:17-slim-bullseye"
    }

    to {
        image = System.getenv("BACKEND_IMAGE") ?: ""
        tags = setOf("latest")
    }

    container {
        ports = listOf("7369")
        mainClass = "tech.procd.MainKt"
    }
}
ktor {
    fatJar {
        archiveFileName.set("fat.jar")
    }
}

tasks.test {
    useJUnitPlatform()
}
