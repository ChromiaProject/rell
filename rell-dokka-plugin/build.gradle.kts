plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// A logging backend for the `run` task only: the published artifact must not carry an SLF4J
// provider, or consumers (chromia-cli's `chr doc`) end up with two on the classpath.
val cliLogging: Configuration by configurations.creating

dependencies {
    implementation(libs.kotlinx.html)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.autolink)

    implementation(projects.rellApiBase)
    implementation(projects.rellBase)

    implementation(libs.clikt)
    cliLogging(libs.slf4j.simple)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertk)
    testImplementation(libs.log4j.slf4j2.impl)
}

application.mainClass = "com.chromia.rell.dokka.cli.MainKt"

tasks.named<JavaExec>("run") {
    classpath += cliLogging
}

tasks.test {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

tasks.withType<JacocoReport> {
    dependsOn(tasks.test)

    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.map { fileTree(it).apply { exclude("**/cli/*") } }))
    }
}
