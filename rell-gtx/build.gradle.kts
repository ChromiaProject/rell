plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

description = "Rell integration with Postchain GTX: transaction handling, database init, and operation execution"

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

val generateTestResources by tasks.registering(Copy::class) {
    val rellVersion = project.version.toString().removeSuffix("-SNAPSHOT")
    inputs.property("rellVersion", rellVersion)
    from(layout.projectDirectory.dir("src/test/templates"))
    into(layout.buildDirectory.dir("generated/resources/test"))
    expand("rellVersion" to rellVersion)
}

sourceSets.test {
    resources.srcDir(generateTestResources)
}

dependencies {
    api(projects.rellBase)
    api(projects.rellApiNative)

    api(libs.postchain.spi)

    implementation(libs.jooq)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
    testImplementation(libs.postchain.devtools)
    testImplementation(projects.rellBase.testUtils)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    artifact(testJar)
}
