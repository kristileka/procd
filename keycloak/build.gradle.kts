plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    /** Kotlin */
    implementation(Dependencies.kotlin("stdlib-jdk8"))
    implementation(Dependencies.kotlinCoroutines("jdk8"))
    implementation(Dependencies.kotlinCoroutines("core"))
    implementation(Dependencies.kotlinSerialization("json"))

    /** Ktor */
    implementation(Dependencies.ktor("client-core"))
    implementation(Dependencies.ktor("client-cio"))
    implementation(Dependencies.ktor("client-logging"))

    /** Keycloak */
    implementation(Dependencies.keycloak("admin-client"))
    implementation(Dependencies.keycloak("authz-client"))

    /** Logging */
    implementation(Dependencies.logback("core"))
    implementation(Dependencies.logback("classic"))
    implementation(Dependencies.kotlinLogging())

    /** Netty */
    implementation(Dependencies.netty("resolver-dns-native-macos"))
}
