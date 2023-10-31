plugins {
    kotlin("jvm") version "1.9.10"
    application
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
}

val lsp4jVersion = "0.21.1"
val koinVersion = "3.5.0"
val junitJupiterVersion = "5.10.0"

dependencies {
    implementation(libs.bundles.lsp4j)
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation(libs.bundles.logging)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.27.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}