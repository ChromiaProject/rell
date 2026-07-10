plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.rellApiBase)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertk)
    testImplementation(libs.junit.jupiter)
}

val testConfiguration = configurations.create("testConfiguration")

val testjar = tasks.register<Jar>("testJar") {
    description = "Package test classes into a JAR for consumption by sibling modules."
    group = LifecycleBasePlugin.BUILD_GROUP
    from(sourceSets.test.get().output)
    archiveClassifier = "test"
}

artifacts {
    add(testConfiguration.name, testjar)
}
