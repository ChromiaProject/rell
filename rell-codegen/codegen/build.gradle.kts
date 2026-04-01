plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.rellBase)
    implementation(projects.rellApiBase)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertk)
    testImplementation(libs.junit.jupiter)
}

val testConfiguration by configurations.creating

val testjar by tasks.register<Jar>("testJar") {
    from(sourceSets.test.get().output)
    archiveClassifier.set("test")
}

artifacts {
    add(testConfiguration.name, testjar)
}
