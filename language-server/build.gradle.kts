plugins {
    kotlin("jvm") version "1.9.10"
    application
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.lsp4j)
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.testcontainers)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}