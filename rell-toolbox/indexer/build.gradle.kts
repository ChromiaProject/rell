plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(projects.rellToolbox.ast)
    implementation(projects.rellToolbox.common)
    implementation(projects.rellBase)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}
