/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

description = "Rell interactive REPL and shell"

kotlin {
    explicitApi()
    compilerOptions.optIn.add("net.postchain.rell.api.base.InternalRellApi")
}

dependencies {
    api(projects.rellGtx)
    api(projects.rellApiGtx)

    implementation(libs.jline)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
    testImplementation(project(":rell-base", "testArtifacts"))
    testImplementation(project(":rell-api-base", "testArtifacts"))

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}
