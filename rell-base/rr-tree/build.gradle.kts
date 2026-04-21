plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell RR tree: serializable intermediate representation"

sourceSets {
    main { kotlin.setSrcDirs(listOf("src/main")) }
    test { kotlin.setSrcDirs(listOf("src/test")) }
}

dependencies {
    api(projects.rellBase.utils)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
