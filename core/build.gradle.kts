plugins {
    id("java")
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
    antlr
}

dependencies {
    antlr(libs.antlr)
    implementation(libs.antlr)
    implementation(libs.bundles.rell)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.dokkaHtml {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
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
    java.srcDir("src/main/java")
    kotlin.srcDir("src/main/kotlin")
}
