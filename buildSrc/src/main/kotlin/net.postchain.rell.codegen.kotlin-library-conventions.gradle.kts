
plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("net.postchain.rell.codegen.kotlin-common-conventions")

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

java {
    withSourcesJar()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-test:1.8.21")
}
