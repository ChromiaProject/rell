plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(projects.rellToolbox.common)
    implementation(projects.rellBase)
    implementation(projects.rellApiBase)

    implementation(libs.serpro69.faker)
    implementation(libs.bundles.jackson)
    implementation(libs.json.schema.validator)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}
