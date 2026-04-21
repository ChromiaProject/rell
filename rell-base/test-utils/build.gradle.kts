plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell test utilities used by external modules"

dependencies {
    api(projects.rellBase)
    api(libs.junit.jupiter)
    api(libs.junit.platform.launcher)
    api(kotlin("test-junit5"))
    implementation(projects.rellBase.rrSerialization)
    implementation(libs.log4j.slf4j2.impl)
    implementation(libs.postgresql)
}
