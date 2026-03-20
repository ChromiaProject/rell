plugins {
    id("net.postchain.rell.codegen.kotlin-library-conventions")
}

dependencies {
    implementation(project(":codegen"))
    implementation(libs.rell)
    testImplementation(project(":codegen", "testConfiguration"))
    testImplementation(libs.bundles.testcontainers)
}

sourceSets.getByName("test") {
    resources.srcDir("${rootProject.rootDir}/testResources")
} 