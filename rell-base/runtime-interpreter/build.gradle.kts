plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell runtime interpreter: tree-walk dispatch on the RR_ tree, REPL, test runner"

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}

// Strip Kotlin's auto-inserted null-check intrinsics from this module's bytecode.
// `Intrinsics.checkNotNullParameter` and similar functions build error-message strings via `StringBuilder`,
// which Graal's partial evaluator follows into JDK's SecurityManager/Locale/StringBuilder cycle and aborts
// compilation.
// Stripping these checks is safe for an internal-only module that is not to be called from Java.
kotlin.compilerOptions.freeCompilerArgs.addAll(
    "-Xno-param-assertions",
    "-Xno-call-assertions",
    "-Xno-receiver-assertions",
)

dependencies {
    api(projects.rellBase.runtimeCore)

    implementation(libs.kotlinLogging)
    implementation(libs.jackson.databind)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle)
}
