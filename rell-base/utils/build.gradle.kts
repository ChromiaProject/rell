plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell utilities: collection typealiases, name types, common helpers"

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}

dependencies {
    api(libs.kotlinx.collections.immutable)
    api(libs.guava)
    api(libs.postchain.gtv)
}
