plugins {
    alias(libs.plugins.kotlin.jvm)
    antlr
}

val generateTestCases: Boolean by rootProject.extra

dependencies {
    antlr(libs.antlr)
    api(libs.antlr.runtime)
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(projects.rellBase)
    implementation(libs.guava)
    implementation(libs.commons.collections4)
    implementation(projects.rellToolbox.common)

    testImplementation(libs.bundles.jackson)
    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}

val testCasesDir = layout.buildDirectory.dir("rell-test-cases")

// Copy test cases directly from project build directories (avoids zipTree config cache issues)
val copyTestCases by tasks.registering(Copy::class) {
    enabled = generateTestCases
    if (generateTestCases) {
        dependsOn(
            project(":rell-api-gtx").tasks.named("test"),
            project(":rell-base").tasks.named("test"),
            project(":rell-gtx").tasks.named("test"),
            project(":rell-api-base").tasks.named("test"),
        )
        from(project(":rell-api-gtx").layout.buildDirectory.dir("rell-test-cases"))
        from(project(":rell-base").layout.buildDirectory.dir("rell-test-cases"))
        from(project(":rell-gtx").layout.buildDirectory.dir("rell-test-cases"))
        from(project(":rell-api-base").layout.buildDirectory.dir("rell-test-cases"))
    }
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
