plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(libs.kotlinx.html)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.autolink)

    implementation(projects.rellApiBase)
    implementation(projects.rellBase)

    implementation(libs.clikt)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertk)
    testImplementation(libs.jsoup)
    testImplementation(libs.jackson.kotlin)
    testImplementation(libs.log4j.slf4j2.impl)
}

application.mainClass = "com.chromia.rell.dokka.cli.MainKt"

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
