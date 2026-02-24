/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.git.properties)
    jacoco
    application
}

kotlin {
    compilerOptions.optIn.add("net.postchain.rell.api.base.InternalRellApi")
}

application.mainClass = "net.postchain.rell.tools.RellToolsMain"

private val publishDist = providers.gradleProperty("publishDist").isPresent

dependencies {
    api(projects.rellGtx)
    api(projects.rellApiShell)

    implementation(libs.picocli)

    runtimeOnly(libs.log4j.slf4j2.impl)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(project(":rell-base", "testArtifacts"))
    testImplementation(project(":rell-gtx", "testArtifacts"))
    testImplementation(project(":rell-api-base", "testArtifacts"))
}

gitProperties {
    gitPropertiesName = "rell-tools-maven.properties"
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
    customProperty("project.groupId", project.group.toString())
    customProperty("project.artifactId", project.name)
    customProperty("project.version", project.version.toString())
}

val generateDependencyList by tasks.registering {
    group = "build"
    description = "Generates dependency list file"

    val outputDir = layout.buildDirectory.dir("generated/resources/dependencies")
    val outputFile = outputDir.map { it.file("rell-tools-dependencies.txt") }
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

// Shared copy spec for Rell distribution layout
val rellDistCopySpec: CopySpec = copySpec {
    into("postchain-node") {
        into("lib") {
            from(tasks.jar)
            from(configurations.runtimeClasspath)
        }
        from("src/main/scripts") {
            include("*.sh")
            filePermissions {
                unix("0755")
            }
        }
        from("src/main/scripts") {
            include("*.cmd")
        }
    }
}

// Custom distribution tasks (separate from application plugin's distTar/distZip)
val rellDistTar by tasks.registering(Tar::class) {
    group = "distribution"
    description = "Creates Rell distribution tar.gz"
    archiveClassifier = "dist"
    compression = Compression.GZIP
    with(rellDistCopySpec)
}

val rellDistZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Creates Rell distribution zip"
    archiveClassifier = "dist"
    with(rellDistCopySpec)
}

// Task to install/unpack the Rell distribution for local development use
val installRellDist by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Installs Rell distribution for local development (used by work/*.sh scripts)"
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("install/rell-dist"))
    with(rellDistCopySpec)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    if (publishDist) {
        artifact(rellDistTar)
        artifact(rellDistZip)
    }
}
