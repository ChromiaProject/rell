import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.10"
    application
}

group = "net.postchain.rell.toolbox"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven {
        name = "bintray"
        url = uri("https://jcenter.bintray.com")
    }
    maven {
        name = "etherjar"
        url = uri("https://maven.emrld.io")
    }
    maven {
        name = "Rell GitLab Registry"
        url = uri("https://gitlab.com/api/v4/projects/32802097/packages/maven")
    }
    maven {
        name = "Postchain GitLab Registry"
        url = uri("https://gitlab.com/api/v4/projects/32294340/packages/maven")
    }
}

val lsp4jVersion = "0.21.1"
val koinVersion = "3.5.0"
val rellVersion = "0.13.1"

dependencies {
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:$lsp4jVersion")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.websocket:$lsp4jVersion")

    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    implementation("org.antlr:antlr4:4.13.1")
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.3")
    // TODO: check if only ANTLR4 runtime if enough
    // implementation("org.antlr:antlr4-runtime:4.13.1")

    implementation(group="net.postchain.rell", name="rell", version=rellVersion, ext="pom")
    implementation("net.postchain.rell:rell-base:$rellVersion")
    implementation("net.postchain.rell:rell-tools:$rellVersion")

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.0.1")

    testImplementation(kotlin("test"))
    //testImplementation("io.insert-koin:koin-test:$koinVersion")

    // In case of JUnit 5
    //  testImplementation "io.insert-koin:koin-test-junit5:$koinVersion"
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

kotlin {
    jvmToolchain(17)
}