plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

tasks.test {
    useJUnitPlatform()
}
