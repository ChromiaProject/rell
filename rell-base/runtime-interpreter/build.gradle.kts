plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell runtime interpreter: tree-walk dispatch on the RR_ tree, REPL, test runner"

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}

// Strip Kotlin's null-check intrinsics from this module. They show up as
// `Intrinsics.checkNotNullParameter` calls in compiled bytecode, whose failure path builds
// a String via StringBuilder. Graal's partial evaluator follows that into the JDK
// `StringBuilder.appendNull → SecurityManager → Locale → ...` cycle and aborts compilation
// with "Too deep inlining". Both the tree-walker (this module) and the Truffle peer
// (runtime-truffle) participate in compiled code paths that PE traverses, so both modules
// must drop the intrinsics to keep PE clean. Type-system guarantees and call-site
// discipline already make these runtime null checks redundant.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
        )
    }
}

dependencies {
    api(projects.rellBase.runtimeCore)

    implementation(libs.kotlinLogging)
    implementation(libs.jackson.databind)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle)
}
