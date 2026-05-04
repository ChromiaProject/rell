plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell utilities: collection typealiases, name types, common helpers"

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}

// `R_StackPos` and other identifier types get inlined into the Truffle hot path through
// `Rt_CallFrame.error`. See runtime-core/build.gradle.kts for the full rationale.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
}

dependencies {
    api(libs.kotlinx.collections.immutable)
    api(libs.guava)
    api(libs.postchain.gtv)
}
