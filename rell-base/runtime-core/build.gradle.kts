plugins {
    alias(libs.plugins.kotlin.jvm)
}

description =
    "Rell runtime core: shared runtime primitives, value types, stdlib, SQL generation, GTV plumbing"

// Strip Kotlin's auto-inserted null-check intrinsics from this module's bytecode.
// `Intrinsics.checkNotNullParameter` and similar functions build error-message strings via `StringBuilder`,
// which Graal's partial evaluator follows into JDK's SecurityManager/Locale/StringBuilder cycle and aborts
// compilation.
// Stripping these checks is safe for an internal-only module that is not to be called from Java.
kotlin.compilerOptions.freeCompilerArgs.addAll(
    "-Xno-param-assertions",
    "-Xno-call-assertions",
    "-Xno-receiver-assertions",
    "-XXLanguage:+UnnamedLocalVariables",
)

dependencies {
    api(projects.rellBase.frontend)
    api(libs.gtv.min.core)
    // Crypto (CryptoSystem, KeyPair, Signature) and BlockchainRid/WrappedByteArray, both of which
    // appear in this module's public API.
    api(libs.postchain.common)
    implementation(libs.gtv.min.json.jackson)
    // `Rt_ValueClass.nativeTypes` builds KTypes via `KClass.createType()`. Previously came in
    // transitively through postchain-gtv's reflective object mapper.
    implementation(kotlin("reflect"))
    implementation(libs.jackson.core)
    implementation(libs.jooq)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle)
}
