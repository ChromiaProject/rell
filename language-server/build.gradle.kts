plugins {
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
    id("com.gradleup.shadow") version "8.3.2"
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

    implementation(libs.chromia.build.tools) {
        exclude(group = "com.chromia.rell.dokka")
        exclude(group = "net.postchain", module = "postchain-base")
        exclude(group = "net.postchain", module = "postchain-admin-service")
        exclude(group = "net.postchain", module = "postchain-gtv")
        exclude(group = "net.postchain", module = "chromia-client")
        exclude(group = "net.postchain.client", module = "postchain-client")
        exclude(group = "net.postchain.rell", module = "codegen")
        exclude(group = "org.eclipse.jgit")
    }

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
