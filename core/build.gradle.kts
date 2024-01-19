plugins {
    id("java")
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm")
    antlr
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
    antlr(libs.antlr)
    implementation(libs.antlr)
    implementation(libs.bundles.rell)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}

tasks.test {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    arguments = arguments + listOf(
        "-visitor",
        "-long-messages",
        "-package", "net.postchain.rell.toolbox.core.parser"
    )
    outputDirectory = file("src/main/gen")
}


sourceSets.getByName("main") {
    java.srcDir("src/main/gen")
    java.srcDir("src/main/java")
    kotlin.srcDir("src/main/kotlin")
}

sourceSets.getByName("test") {
    output.setResourcesDir(file("build/classes/kotlin/test"))
}
