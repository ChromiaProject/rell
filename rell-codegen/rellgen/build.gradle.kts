plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(libs.commons.text)
    implementation(projects.rellCodegen.codegen)
    implementation(projects.rellCodegen.codegenKotlin)
    implementation(projects.rellCodegen.codegenTypescript)
    implementation(projects.rellCodegen.codegenJavascript)
    implementation(projects.rellCodegen.codegenMermaid)
    implementation(projects.rellCodegen.codegenPython)
    implementation(libs.clikt)
    implementation(projects.rellApiBase)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertk)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.log4j.slf4j2.impl)
}

application {
    mainClass.set("net.postchain.rell.codegen.app.AppKt")
}

