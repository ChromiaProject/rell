plugins {
    id("net.postchain.rell.codegen.kotlin-library-conventions")
}

dependencies {
    implementation(project(":codegen"))
    implementation(libs.rell)
    testImplementation(project(":codegen", "testConfiguration"))
    testImplementation("org.testcontainers:testcontainers:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}

val copy by tasks.register<Copy>("copy-resources") {
    from("${rootProject.rootDir}/testResources")
    into("${project.buildDir}/resources/test")
}

val test by tasks.getting {
    dependsOn(copy)
}