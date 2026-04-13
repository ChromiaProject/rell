import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

description = "Rell integration with Postchain GTX: transaction handling, database init, and operation execution"

// Include Java files in kotlin source directories (for mixed Java/Kotlin compilation)
sourceSets.test {
    java {
        srcDir("src/test/kotlin")
    }
}

// Configuration for sharing test code with other modules (similar to Maven's test-jar)
val testJar by tasks.registering(Jar::class) {
    archiveClassifier = "tests"
    from(sourceSets.test.get().output)
}

configurations.create("testArtifacts") {
    extendsFrom(configurations.testRuntimeClasspath.get())
}

artifacts {
    add("testArtifacts", testJar)
}

tasks.processTestResources {
    val rellVersion = project.version.toString().removeSuffix("-SNAPSHOT")
    eachFile {
        if (name == "snapshot_config.xml") {
            expand("rellVersion" to rellVersion)
        }
    }
}

dependencies {
    api(projects.rellBase)
    api(projects.rellApiNative)

    api(libs.postchain.spi)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
    testImplementation(libs.postchain.devtools)
    testImplementation(project(":rell-base", "testArtifacts"))
}

publishing.publications.named<MavenPublication>("mavenJava") {
    artifact(testJar)
}
