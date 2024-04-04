plugins {
    id("net.postchain.rell.codegen.kotlin-library-conventions")
}

dependencies {
    implementation(project(":codegen"))
    implementation(libs.rell)
    testImplementation(project(":codegen", "testConfiguration"))
    testImplementation(libs.bundles.testcontainers)
}

val copy by tasks.register<Copy>("copy-resources") {
    from("${rootProject.rootDir}/testResources")
    into("${layout.buildDirectory}/resources/test")
}

val test by tasks.getting {
    dependsOn(copy)
}