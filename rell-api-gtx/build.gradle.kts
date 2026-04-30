plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

description = "Rell GTX testing API: test runners and SQL test infrastructure"

kotlin {
    explicitApi()
    compilerOptions.optIn.add("net.postchain.rell.api.base.InternalRellApi")
}

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

// Outgoing configuration exposing aggregated Rell test-case directories to sibling modules
// (consumed by :rell-toolbox:ast for grammar correctness tests).
val rellTestCases by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val rellTestCaseTests = listOf<Any>(
    tasks.test,
    ":rell-base:test",
    ":rell-gtx:test",
    ":rell-api-base:test",
)

val rellTestCaseDirs = listOf(
    layout.buildDirectory.dir("rell-test-cases"),
    project(":rell-base").layout.buildDirectory.dir("rell-test-cases"),
    project(":rell-gtx").layout.buildDirectory.dir("rell-test-cases"),
    project(":rell-api-base").layout.buildDirectory.dir("rell-test-cases"),
)

rellTestCaseDirs.forEach { dir ->
    artifacts.add(rellTestCases.name, dir) {
        builtBy(rellTestCaseTests)
    }
}

dependencies {
    api(projects.rellGtx)
    api(projects.rellApiBase)

    api(libs.postchain.base)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
    testImplementation(projects.rellBase.testUtils)
    testImplementation(project(":rell-api-base", "testArtifacts"))
}

publishing.publications.named<MavenPublication>("mavenJava") {
    artifact(testJar)
}
