plugins {
    kotlin("jvm") version "1.9.10"
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
}
val koinVersion = "3.5.0"

dependencies {
    implementation("org.antlr:antlr4:4.13.1")
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    testImplementation(libs.bundles.testcontainers)
    implementation(libs.bundles.logging)
}

tasks.test {
    useJUnitPlatform()
}

sourceSets.getByName("main") {
    java.srcDir("src/main/gen")
    java.srcDir("src/main/java")
    java.srcDir("src/main/kotlin")
}