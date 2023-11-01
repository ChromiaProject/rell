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
    implementation(libs.bundles.koin)

    testImplementation(libs.bundles.testcontainers)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}