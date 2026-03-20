plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(projects.rellToolbox.common)
    implementation(projects.rellToolbox.ast)
    implementation(projects.rellToolbox.indexer)
    implementation(projects.rellBase)
    implementation(libs.java.diff.utils)
    implementation(libs.ec4j)

    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}
