import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

val sentryEnabled = System.getenv("SENTRY_AUTH_TOKEN") != null

plugins {
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
    id("com.gradleup.shadow") version "8.3.2"
    application
    id("jacoco-report-aggregation")
    id("io.sentry.jvm.gradle") version "4.13.0"
}

sentry {
    // Generates a JVM (Java, Kotlin, etc.) source bundle and uploads your source code to Sentry.
    // This enables source context, allowing you to see your source
    // code as part of your stack traces in Sentry.
    includeSourceContext = sentryEnabled
    org = "chromaway-ab-za"
    projectName = "rell-toolbox"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
    System.setProperty("sentry.release", "${project.version}")
}

dependencies {
    implementation(libs.sentry.log4j2)
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

tasks.sourcesJar {
    dependsOn(tasks.generateSentryDebugMetaPropertiesjava, tasks.collectExternalDependenciesForSentry)
}

application {
    mainClass.set("net.postchain.rell.toolbox.lsp.StdioMainKt")
}

tasks.jar {
    manifest.attributes["Multi-Release"] = true
}

tasks.shadowJar{
    manifest.attributes["Multi-Release"] = true
    transform(Log4j2PluginsCacheFileTransformer::class.java)
    mergeServiceFiles()
}

publishing {
    publications {
        create<MavenPublication>("rell-language-server") {
            artifactId = "rell-language-server"
            shadow.component(this)
        }
    }
}
