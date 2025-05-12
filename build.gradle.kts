import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Dependencies.KOTLIN_VERSION}")
        classpath("com.google.cloud.tools:jib-gradle-plugin:${Dependencies.JIB}")
    }
}

repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm") version Dependencies.KOTLIN_VERSION
    kotlin("plugin.serialization") version Dependencies.KOTLIN_VERSION

    id("com.github.ben-manes.versions") version Dependencies.BEN_MANES_VERSION
    id("com.google.cloud.tools.jib") version Dependencies.JIB
}

dependencies {
    testImplementation(kotlin("test"))
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = Dependencies.JVM_TARGET
        languageVersion = Dependencies.LANGUAGE_VERSION
        allWarningsAsErrors = true
    }
}

tasks.test {
    useJUnitPlatform()
}



fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}
