plugins {
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
    id("jacoco-report-aggregation")
}

dependencies {
    implementation(libs.bundles.lsp4j)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.koin)
    implementation(project(":core"))
    implementation(libs.bundles.rell)

    implementation("org.furyio:fury-core:0.4.1")
    implementation(libs.ec4j)
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")

    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.testing)
    testImplementation(libs.rell.api.base)
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("testCodeCoverageReport"))
}

application {
    mainClass.set("net.postchain.rell.toolbox.lsp.StdioMainKt")
}

tasks.jar {
    manifest.attributes["Multi-Release"] = true
}

publishing {
    publications {
        create<MavenPublication>("rell-language-server") {
            artifactId = "rell-language-server"
            shadow.component(this)
        }
    }
}
