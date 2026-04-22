import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

val sentryEnabled = System.getenv("SENTRY_AUTH_TOKEN") != null

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.shadow)
    alias(libs.plugins.sentry)
}

sentry {
    includeSourceContext = sentryEnabled
    org = "chromaway-ab-za"
    projectName = "rell-toolbox"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("projectVersion", projectVersion)
    eachFile {
        if (name == "sentry.properties") {
            expand("version" to projectVersion)
        }
    }
}

dependencies {
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(libs.sentry.log4j2)
    implementation(libs.bundles.lsp4j)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.koin)
    implementation(projects.rellToolbox.ast)
    implementation(projects.rellToolbox.common)
    implementation(projects.rellToolbox.indexer)
    implementation(projects.rellToolbox.codeQuality)
    implementation(projects.rellBase)

    implementation(libs.fury.core)

    implementation(libs.chromia.build.tools) {
        exclude(group = "com.chromia.rell.dokka")
        exclude(group = "net.postchain", module = "postchain-base")
        exclude(group = "net.postchain", module = "postchain-admin-service")
        exclude(group = "net.postchain", module = "postchain-gtv")
        exclude(group = "net.postchain.client", module = "chromia-client")
        exclude(group = "net.postchain.client", module = "postchain-client")
        exclude(group = "net.postchain.rell", module = "codegen")
        exclude(group = "org.eclipse.jgit")
    }

    testImplementation(libs.testcontainers)
    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(projects.rellApiBase)
}

tasks.sourcesJar {
    dependsOn(tasks.generateSentryDebugMetaPropertiesjava, tasks.collectExternalDependenciesForSentry)
}

application.mainClass = "net.postchain.rell.toolbox.lsp.StdioMainKt"

tasks.jar {
    manifest.attributes["Multi-Release"] = true
    manifest.attributes["Implementation-Title"] = project.name
    manifest.attributes["Implementation-Version"] = project.version
    manifest.attributes["Implementation-Vendor"] = "Chromaway AB"
}

tasks.shadowJar {
    manifest.attributes["Multi-Release"] = true
    transform(Log4j2PluginsCacheFileTransformer::class.java)
    mergeServiceFiles()
}
