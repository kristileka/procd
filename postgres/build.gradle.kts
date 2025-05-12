plugins {
    kotlin("jvm")
}

dependencies {
    /** Kotlin */
    implementation(project(":common"))
    implementation(Dependencies.kotlin("stdlib-jdk8"))
    implementation(Dependencies.kotlinCoroutines("jdk8"))
    implementation(Dependencies.kotlinCoroutines("core"))

    /** Vertx */
    implementation(Dependencies.vertx("lang-kotlin"))
    implementation(Dependencies.vertx("lang-kotlin-coroutines"))
    implementation(Dependencies.vertx("sql-client"))
    implementation(Dependencies.vertx("pg-client"))

    /** Netty */
    implementation(Dependencies.netty("resolver-dns-native-macos"))
}
