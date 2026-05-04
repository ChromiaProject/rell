plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell Truffle backend: peer to runtime-interpreter, partial-evaluation-friendly AST nodes on top of GraalVM Truffle"

// Strip Kotlin's auto-inserted null-check intrinsics from this module's bytecode.
// `Intrinsics.checkNotNullParameter` and similar functions build error-message strings via `StringBuilder`,
// which Graal's partial evaluator follows into JDK's SecurityManager/Locale/StringBuilder cycle and aborts
// compilation.
// Stripping these checks is safe for an internal-only module that is not to be called from Java.
kotlin.compilerOptions {
    freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
}

dependencies {
    api(projects.rellBase.runtimeCore)
    implementation(projects.rellBase.runtimeInterpreter)

    implementation(libs.truffle.api)

    // Pull truffle-runtime onto the runtime classpath so the Graal-compiled code path engages
    // when running under GraalVM CE. On stock HotSpot this still works, just without partial
    // evaluation (interpreter mode).
    runtimeOnly(libs.truffle.runtime)

    implementation(libs.kotlinLogging)
}
