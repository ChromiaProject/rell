plugins {
    id("java")
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
    antlr
}

val rellTestCasesConfiguration by configurations.creating

dependencies {
    antlr(libs.antlr)
    implementation(libs.antlr)
    implementation(libs.ec4j)
    implementation(libs.bundles.rell)
    implementation(libs.bundles.jackson)
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("com.chromia.cli:chromia-build-tools:0.20.6") {
        exclude(group = "com.chromia.rell.dokka")
        exclude(group = "net.postchain", module = "postchain-base")
        exclude(group = "net.postchain", module = "postchain-admin-service")
        exclude(group = "net.postchain", module = "postchain-gtv")
        exclude(group = "net.postchain", module = "chromia-client")
        exclude(group = "net.postchain.client", module = "postchain-client")
        exclude(group = "net.postchain.rell", module = "codegen")
        exclude(group = "org.eclipse.jgit")
    }


    rellTestCasesConfiguration(
        group = "net.postchain.rell",
        name = "rell-api-gtx",
        version = libs.versions.rell.get(),
        classifier = "rell-test-cases",
        ext = "zip"
    )

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
        "-package", "net.postchain.rell.toolbox.core.parser"
    )
    outputDirectory = layout.buildDirectory.dir("generated/antlr").get().asFile
}

sourceSets.getByName("main") {
    java.srcDir(tasks.generateGrammarSource)
}

sourceSets.getByName("test") {
    resources.srcDir(testCasesDir)
}
