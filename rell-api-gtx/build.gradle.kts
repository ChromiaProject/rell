import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

description = "Rell GTX testing API: test runners and SQL test infrastructure"

kotlin {
    explicitApi()
    compilerOptions.optIn.add("net.postchain.rell.api.base.InternalRellApi")
}

val generateTestCases: Boolean by rootProject.extra

// Configuration for sharing test code with other modules (similar to Maven's test-jar)
val testJar by tasks.registering(Jar::class) {
    archiveClassifier = "tests"
    from(sourceSets.test.get().output)
}

configurations.create("testArtifacts") {
    extendsFrom(configurations.testRuntimeClasspath.get())
}

artifacts {
    add("testArtifacts", testJar)
}

dependencies {
    api(projects.rellGtx)
    api(projects.rellApiBase)

    api(libs.postchain.base)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
    testImplementation(project(":rell-base", "testArtifacts"))
    testImplementation(project(":rell-api-base", "testArtifacts"))
}

// Aggregate Rell test cases when -PgenerateTestCases is set (replacement for Maven assembly)
val rellTestCasesArchive by tasks.registering(Zip::class) {
    archiveClassifier = "rell-test-cases"
    enabled = generateTestCases
    dependsOn(
        tasks.test,
        project(":rell-base").tasks.test,
        project(":rell-gtx").tasks.test,
        project(":rell-api-base").tasks.test,
    )
    from(layout.buildDirectory.dir("rell-test-cases"))
    from(project(":rell-base").layout.buildDirectory.dir("rell-test-cases"))
    from(project(":rell-gtx").layout.buildDirectory.dir("rell-test-cases"))
    from(project(":rell-api-base").layout.buildDirectory.dir("rell-test-cases"))
}

publishing.publications.named<MavenPublication>("mavenJava") {
    artifact(testJar)
    if (generateTestCases) {
        artifact(rellTestCasesArchive)
    }
}
