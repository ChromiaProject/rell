plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

description = "Rell compilation API and GTV configuration generation"

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

dependencies {
    api(projects.rellBase)

    // Experimental Truffle execution backend. Kept off the compile classpath (selected reflectively
    // by RellApiInterpreterBackend); `runtimeOnly` puts it on the runtime classpath of every API
    // consumer, so the `-Drell.execution.backend=truffle` dev switch works without consumer changes.
    runtimeOnly(projects.rellBase.runtimeTruffle)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
    testImplementation(projects.rellBase.testUtils)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    artifact(testJar)
}
