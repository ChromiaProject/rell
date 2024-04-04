plugins {
    id("net.postchain.rell.codegen.kotlin-library-conventions")
}

dependencies {
    implementation(project(":codegen"))
    implementation(libs.rell)
    implementation(libs.postchain.gtv)
    implementation(libs.postchain.common)
    implementation(libs.postchain.client)
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    testImplementation(project(":codegen", "testConfiguration"))
}

val copy by tasks.register<Copy>("copy-resources") {
    from("${rootProject.rootDir}/testResources")
    into("${layout.buildDirectory}/resources/test")
}

val test by tasks.getting {
    dependsOn(copy)
}