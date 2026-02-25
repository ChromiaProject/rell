plugins {
    id("java")
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
    antlr
}

val rellTestCasesConfiguration by configurations.creating

dependencies {
    antlr(libs.antlr)
    api(libs.antlr.runtime)
    implementation(libs.rell.base)
    implementation(libs.guava)
    implementation(libs.commons.collections4)
    implementation(project(":common"))

    rellTestCasesConfiguration(
        group = "net.postchain.rell",
        name = "rell-api-gtx",
        version = libs.versions.rell.get(),
        classifier = "rell-test-cases",
        ext = "zip"
    )

    testImplementation(libs.bundles.jackson)
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
}

val testCasesDir = layout.buildDirectory.dir("rell-test-cases")

val copyTestCases by tasks.registering(Copy::class) {
    from({ zipTree(rellTestCasesConfiguration.singleFile) })
    into(testCasesDir.map { it.dir("test-cases") })
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.dokkaHtml {
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
        "-package", "net.postchain.rell.toolbox.parser"
    )
    outputDirectory = layout.buildDirectory.dir("generated/antlr").get().asFile
}

sourceSets.getByName("test") {
    resources.srcDir(testCasesDir)
}

sourceSets.getByName("main") {
    java.srcDir(tasks.generateGrammarSource)
}

// Workaround excluding antlr "non-runtime" dependencies from jar.
// Adjust it when antlr gradle plugin will be fixed
// https://github.com/gradle/gradle/issues/820#issuecomment-288838412
configurations {
    api {
        setExtendsFrom(extendsFrom.filterNot { it == antlr.get() })
    }
}
