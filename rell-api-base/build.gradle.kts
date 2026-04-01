import org.gradle.api.publish.maven.MavenPublication

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
    implementation(projects.rellBase)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
    testImplementation(project(":rell-base", "testArtifacts"))
}

publishing.publications.named<MavenPublication>("mavenJava") {
    artifact(testJar)
}
