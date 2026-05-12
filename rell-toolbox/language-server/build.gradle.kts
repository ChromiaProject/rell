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

val generateMainResources by tasks.registering(Copy::class) {
    val projectVersion = project.version.toString()
    inputs.property("projectVersion", projectVersion)
    from(layout.projectDirectory.dir("src/main/templates"))
    into(layout.buildDirectory.dir("generated/resources/main"))
    expand("version" to projectVersion)
}

sourceSets.main {
    resources.srcDir(generateMainResources)
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
