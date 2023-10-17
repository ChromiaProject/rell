import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.10"
    application
}

group = "net.postchain.rell.toolbox"

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

dependencies {

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.3")
    // TODO: check if only ANTLR4 runtime if enough
    // implementation("org.antlr:antlr4-runtime:4.13.1")

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.0.1")
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