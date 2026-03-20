plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.rellCodegen.codegen)
    implementation(projects.rellApiBase)
    implementation(libs.postchainClient)
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.assertk)
    testImplementation(libs.junit.jupiter)
    testImplementation(project(":rell-codegen:codegen", "testConfiguration"))
    testImplementation(libs.log4j.slf4j2.impl)
}

sourceSets.getByName("test") {
    resources.srcDir("${projectDir.parentFile}/testResources")
}
