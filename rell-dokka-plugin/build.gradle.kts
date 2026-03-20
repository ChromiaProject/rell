group = "com.chromia.rell.dokka"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

val dependenciesToUnshade: Configuration by configurations.creating
val unshadedClassesTarget = layout.buildDirectory.dir("unshaded")

dependencies {
    implementation(enforcedPlatform(libs.jackson.bom))
    implementation(libs.dokka.core)
    implementation(libs.dokka.base)
    implementation(libs.dokka.analysis.markdown)
    implementation(libs.dokka.analysis.kotlin.api)

    implementation(libs.kotlinx.html)
    implementation(libs.kotlinx.serialization.json)

    implementation(projects.rellApiBase)
    implementation(projects.rellBase)

    implementation(libs.clikt)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertk)
    testImplementation(libs.dokka.test.api)
    testImplementation(libs.dokka.base.test.utils)
    testImplementation(libs.jsoup)
    testImplementation(libs.jackson.kotlin)
    testImplementation(libs.log4j.slf4j2.impl)

    // Unshading dependencies
    dependenciesToUnshade(libs.dokka.analysis.kotlin.descriptors)
    implementation(files(unshadedClassesTarget))
}

val copyShadedDependencyClasses by tasks.registering(Copy::class) {
    from(zipTree(dependenciesToUnshade.singleFile))
    include(
        "**/DocumentableSourceLanguageParser.class",
        "**/DocumentableLanguage.class",
        "**/DescriptorDocumentableSource.class",
        "**/DeclarationDescriptor.class",
    )
    into(unshadedClassesTarget)
}

tasks.compileKotlin {
    dependsOn(copyShadedDependencyClasses)
}

application {
    mainClass = "com.chromia.rell.dokka.cli.MainKt"
}

tasks.test {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

tasks.withType<JacocoReport> {
    dependsOn(tasks.test)
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it).apply {
                exclude("**/cli/*")
            }
        }))
    }
}

