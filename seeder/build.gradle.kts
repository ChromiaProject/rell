plugins {
    id("java")
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.rell.api.base)

    implementation(libs.kotlin.faker)
    implementation(libs.bundles.jackson)
    implementation(libs.json.schema.validator)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
}
