plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":postgres"))

    /** Kotlin */
    implementation(Dependencies.kotlin("stdlib-jdk8"))
    implementation(Dependencies.kotlinCoroutines("jdk8"))
    implementation(Dependencies.kotlinCoroutines("core"))

    /** Vertx */
    implementation(Dependencies.vertx("lang-kotlin"))
    implementation(Dependencies.vertx("lang-kotlin-coroutines"))
    implementation(Dependencies.vertx("sql-client"))
    implementation(Dependencies.vertx("pg-client"))

    /** Logging */
    implementation(Dependencies.logback("core"))
    implementation(Dependencies.logback("classic"))
    implementation(Dependencies.kotlinLogging())

    /** Netty */
    implementation(Dependencies.netty("resolver-dns-native-macos"))
}
