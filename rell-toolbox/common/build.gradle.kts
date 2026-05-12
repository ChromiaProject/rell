plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.antlr.runtime)
    implementation(libs.ec4j)
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(libs.bundles.jackson)
    implementation(projects.rellBase)

    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}
