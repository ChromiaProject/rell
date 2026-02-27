/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.git.properties)
    jacoco
}

description = "Rell core: compiler, runtime, type system, and standard library"

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

dependencies {
    api(libs.postchain.gtv)

    api(kotlin("stdlib"))
    api(kotlin("reflect"))

    implementation(libs.kotlin.logging)
    implementation(libs.guava)
    api(libs.better.parse)
    implementation(libs.jackson.databind)
    implementation(libs.jooq)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle)
    api(libs.kotlinx.collections.immutable)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
}

// The gradle-git-properties plugin does not declare customProperty values as task inputs,
// so Gradle's build cache can serve stale outputs missing those properties. We collect them
// in a map and register them both as custom properties and as explicit task inputs.
val gitCustomProperties = mapOf(
    "project.groupId" to project.group.toString(),
    "project.artifactId" to project.name,
    "project.version" to project.version.toString(),
    "kotlin.version" to libs.versions.kotlin.get(),
    "postchain.version" to libs.versions.postchain.get(),
)

gitProperties {
    gitPropertiesName = "rell-base-maven.properties"
    keys = listOf(
        "git.branch",
        "git.commit.id",
        "git.commit.id.abbrev",
        "git.commit.id.describe",
        "git.commit.message.short",
        "git.commit.message.full",
        "git.commit.time",
        "git.dirty",
        "git.build.version",
    )
    for ((k, v) in gitCustomProperties) customProperty(k, v)
}

tasks.named("generateGitProperties") {
    for ((k, v) in gitCustomProperties) inputs.property("customProperty.$k", v)
}

sourceSets.main {
    // Include Java files in the Kotlin source directory (etherjar classes)
    java {
        srcDir("src/main/kotlin")
    }
}

val generateDependencyList by tasks.registering {
    group = "build"
    description = "Generates dependency list file"

    val outputDir = layout.buildDirectory.dir("generated/resources/dependencies")
    val outputFile = outputDir.map { it.file("rell-base-dependencies.txt") }
    val artifacts = configurations.runtimeClasspath.flatMap { config ->
        config.incoming.artifacts.resolvedArtifacts.map { results ->
            results.map { "${it.id.componentIdentifier}" }.sorted()
        }
    }

    inputs.property("artifacts", artifacts)
    outputs.dir(outputDir)

    doLast {
        outputDir.get().asFile.mkdirs()
        outputFile.get().asFile.writeText(artifacts.get().joinToString("\n"))
    }
}

sourceSets.main {
    resources {
        srcDir(generateDependencyList.map { it.outputs.files.singleFile })
    }
}

tasks.processResources {
    dependsOn(generateDependencyList)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    artifact(testJar)
}
