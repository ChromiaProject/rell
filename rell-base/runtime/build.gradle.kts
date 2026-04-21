plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell runtime: interpreter, SQL, standard library, lmodel DSL, REPL"

dependencies {
    api(projects.rellBase.frontend)

    implementation(libs.kotlinLogging)
    implementation(libs.guava)
    implementation(libs.jackson.databind)
    implementation(libs.jooq)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle)
    implementation(libs.kotlinx.collections.immutable)
}
