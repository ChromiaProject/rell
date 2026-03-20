plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.rellCodegen.codegen)
    implementation(projects.rellApiBase)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertk)
    testImplementation(libs.junit.jupiter)
    testImplementation(project(":rell-codegen:codegen", "testConfiguration"))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.log4j.slf4j2.impl)
}

sourceSets.getByName("test") {
    resources.srcDir("${projectDir.parentFile}/testResources")
}
