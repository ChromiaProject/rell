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
    implementation(project(":formatter"))
    implementation(libs.bundles.rell)

    implementation("org.furyio:fury-core:0.4.1")

    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.testing)
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
