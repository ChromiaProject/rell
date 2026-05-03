plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell runtime core: shared runtime primitives, value types, stdlib, lmodel DSL, SQL generation, GTV plumbing"

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
