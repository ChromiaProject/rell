plugins {
    alias(libs.plugins.kotlin.jvm)
    antlr
}

val rellTestCasesConfiguration by configurations.creating

dependencies {
    antlr(libs.antlr)
    api(libs.antlr.runtime)
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(projects.rellBase)
    implementation(libs.guava)
    implementation(libs.commons.collections4)
    api(projects.rellToolbox.common)

    rellTestCasesConfiguration("net.postchain.rell:rell-api-gtx:${project.version}:rell-test-cases@zip")

    testImplementation(libs.bundles.jackson)
    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}

val testCasesDir = layout.buildDirectory.dir("rell-test-cases")

val copyTestCases by tasks.registering(Copy::class) {
    from({ zipTree(rellTestCasesConfiguration.singleFile) })
    into(testCasesDir.map { it.dir("test-cases") })
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(copyTestCases)
    dependsOn(tasks.generateTestGrammarSource)
}

tasks.compileTestJava {
    dependsOn(tasks.processTestResources)
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}

tasks.generateGrammarSource {
    arguments = arguments + listOf(
        "-visitor",
        "-long-messages",
    )
    packageName = "net.postchain.rell.toolbox.parser"
    outputDirectory = layout.buildDirectory.dir("generated/antlr").get().asFile
}

sourceSets.getByName("test") {
    resources.srcDir(testCasesDir)
}

sourceSets.getByName("main") {
    java.srcDir(tasks.generateGrammarSource)
}

// Workaround excluding antlr "non-runtime" dependencies from jar.
// https://github.com/gradle/gradle/issues/820#issuecomment-288838412
configurations {
    api {
        setExtendsFrom(extendsFrom.filterNot { it == antlr.get() })
    }
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
}
