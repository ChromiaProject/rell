plugins {
    id("java")
    kotlin("jvm") version "1.9.10"
    application
    id("jacoco")
}

version = rootProject.version
group = rootProject.group

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
    maven {
        name = "Chromia parent GitLab Registry"
        url = uri("https://gitlab.com/api/v4/projects/50818999/packages/maven")
    }
}

dependencies {
    implementation(libs.antlr)
    implementation(libs.bundles.logging)
    testImplementation(libs.bundles.testing)
    implementation(project(":core"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}

application {
    mainClass.set("net.postchain.rell.toolbox.formatter.MainKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}