plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell runtime interpreter: tree-walk dispatch on the RR_ tree, REPL, test runner"

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}

dependencies {
    api(projects.rellBase.runtimeCore)

    implementation(libs.kotlinLogging)
    implementation(libs.guava)
    implementation(libs.jackson.databind)
    implementation(libs.jooq)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle)
    implementation(libs.kotlinx.collections.immutable)
}
