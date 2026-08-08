plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell utilities: collection typealiases, name types, common helpers"

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}

// `R_StackPos` and other identifier types get inlined into the Truffle hot path through
// `Rt_CallFrame.error`. See runtime-core/build.gradle.kts for the full rationale.
kotlin.compilerOptions.freeCompilerArgs.addAll(
    "-Xno-param-assertions",
    "-Xno-call-assertions",
    "-Xno-receiver-assertions",
)

dependencies {
    api(libs.kotlinx.collections.immutable)

    // GtvBridge converts between the two GTV implementations, so both appear in its signatures.
    api(libs.gtv.min.core)
    api(libs.postchain.gtv)
}
