import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(libs.bundles.lsp4j)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.koin)
    implementation(projects.rellToolbox.ast)
    implementation(projects.rellToolbox.common)
    implementation(projects.rellToolbox.indexer)
    implementation(projects.rellToolbox.codeQuality)
    implementation(projects.rellBase)

    implementation(libs.fory.core)

    testImplementation(libs.testcontainers)
    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(projects.rellApiBase)
}

application.mainClass = "net.postchain.rell.toolbox.lsp.StdioMainKt"
application.applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")

tasks.jar {
    manifest.attributes["Multi-Release"] = true
    manifest.attributes["Implementation-Title"] = project.name
    manifest.attributes["Implementation-Version"] = project.version
    manifest.attributes["Implementation-Vendor"] = "Chromaway AB"
    // Lets Fory run its zero-Unsafe path on JDK 25+ instead of falling back to sun.misc.Unsafe.
    manifest.attributes["Add-Opens"] = "java.base/java.lang.invoke"
}

tasks.shadowJar {
    manifest.attributes["Multi-Release"] = true
    manifest.attributes["Add-Opens"] = "java.base/java.lang.invoke"
    transform(Log4j2PluginsCacheFileTransformer::class.java)
    mergeServiceFiles()
}
