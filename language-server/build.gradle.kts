plugins {
    kotlin("jvm") version "1.9.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
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
    implementation(libs.bundles.lsp4j)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.koin)
    implementation(project(":core"))
    implementation(project(":formatter"))
    implementation(libs.bundles.rell)

    implementation("org.furyio:fury-core:0.4.1")
    implementation("org.ec4j.core:ec4j-core:0.3.0")

    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.testing)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("net.postchain.rell.toolbox.lsp.StdioMainKt")
}

tasks.jar {
    manifest.attributes["Multi-Release"] = true
}