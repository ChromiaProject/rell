plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell frontend: compiler, model, type system, library descriptors"

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}

dependencies {
    api(projects.rellBase.utils)
    api(projects.rellBase.rrTree)
    api(libs.postchain.gtv)
    api(libs.better.parse)
    implementation(libs.jackson.databind)
    implementation(libs.jooq)
}
