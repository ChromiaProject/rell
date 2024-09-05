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
    implementation(project(":ast"))
    implementation(project(":common"))
    implementation(project(":indexer"))
    implementation(project(":code-quality"))
    implementation(libs.bundles.rell)

    implementation(libs.fury.core)

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
