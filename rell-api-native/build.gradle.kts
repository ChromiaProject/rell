/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

description = "Rell native environment interface for blockchain context"

kotlin {
    explicitApi()
}

dependencies {
    api(libs.postchain.gtv)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
}
