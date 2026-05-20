plugins {
    alias(libs.plugins.kotlin.jvm)
}

val rellTestCasesConfiguration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(libs.antlr.runtime)
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(projects.rellBase)
    implementation(projects.rellBase.frontend)
    implementation(libs.commons.collections4)
    api(projects.rellToolbox.common)

    rellTestCasesConfiguration(project(path = ":rell-api-gtx", configuration = "rellTestCases"))

    testImplementation(libs.bundles.jackson)
    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}

val testCasesDir = layout.buildDirectory.dir("rell-test-cases")

val copyTestCases by tasks.registering(Copy::class) {
    from(rellTestCasesConfiguration)
    into(testCasesDir.map { it.dir("test-cases") })
}

tasks.compileTestKotlin {
    dependsOn(copyTestCases)
}

tasks.compileTestJava {
    dependsOn(tasks.processTestResources)
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}

sourceSets.getByName("test") {
    resources.srcDir(testCasesDir)
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Grammar/parser correctness tests are slow (~6 min) and only relevant when the ANTLR
// grammar or the hand-written compiler parser changes. Excluded from the default `test`
// task and not wired into `check` so CI doesn't pay for them on every build.
// Run manually after grammar/parser edits: `./gradlew :rell-toolbox:ast:grammarTest`.
tasks.test {
    useJUnitPlatform {
        excludeTags("grammar")
    }
}

tasks.register<Test>("grammarTest") {
    description = "Runs ANTLR grammar / parser correctness tests. Slow; not part of `check`."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("grammar")
    }
    shouldRunAfter(tasks.test)
    // Don't trigger jacocoTestReport on this opt-in task; the root build's withType<Test>
    // wires `finalizedBy(jacocoTestReport)` for all Test tasks, which would otherwise pull
    // grammarTest back into `check` via the JacocoReport dependency graph.
    setFinalizedBy(emptyList<Any>())
}
