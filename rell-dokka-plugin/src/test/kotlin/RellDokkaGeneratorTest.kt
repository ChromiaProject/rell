/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import assertk.Assert
import assertk.assertThat
import assertk.assertions.*
import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RellDokkaGeneratorTest {
    private lateinit var projectRoot: File
    private lateinit var targetFolder: File

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        projectRoot = File(this.javaClass.classLoader.getResource("my-rell-dapp")!!.file)
        targetFolder = tempDir.resolve("build").toFile()
        targetFolder.mkdirs()
    }

    @Test
    fun `generated main html page should contains only namespaces from included modules`() {
        val includedModules = listOf("main", "test.datatest", "lib.lib1.core_features")

        val builder = configBuilder(projectRoot.resolve("src"), includedModules).targetFolder(targetFolder)
        RellDokkaGenerator(builder).generate()

        val indexHtmlFile = File(targetFolder, "index.html")
        val htmlContent = indexHtmlFile.readText()
        val anchorLabelRegex = Regex("""anchor-label="([^"]+)"""")
        val documentedNamespaces = anchorLabelRegex.findAll(htmlContent)
                .map { it.groupValues[1] }
                .toList()

        assertThat(documentedNamespaces).each {
            it.isFromAllowedModules(includedModules)
        }
    }


    @Test
    fun `generated main html page should contain included modules and their namespaces`() {
        val additionalModules = listOf("lib.lib1")
        val entryPointModules = listOf("main")

        val builder = configBuilder(
                projectRoot.resolve("src"),
                entryPointModules,
                additionalModules
        ).targetFolder(targetFolder)

        RellDokkaGenerator(builder).generate()

        val indexHtmlFile = File(targetFolder, "index.html")
        val htmlContent = indexHtmlFile.readText()
        val anchorLabelRegex = Regex("""anchor-label="([^"]+)"""")
        val documentedNamespaces = anchorLabelRegex.findAll(htmlContent)
                .map { it.groupValues[1] }
                .toList()

        assertThat(documentedNamespaces).any {
            it.startsWith("lib.lib1.nested")
        }

        assertThat(documentedNamespaces).each {
            it.isFromAllowedModules(entryPointModules + additionalModules)
        }
    }

    @Test
    fun `generated directory structure contains only folders with names of included modules and their submodules`() {
        val additionalModules = listOf("lib.lib1")
        val entryPointModules = listOf("main")

        val builder = configBuilder(
                projectRoot.resolve("src"),
                entryPointModules,
                additionalModules
        ).targetFolder(targetFolder)

        RellDokkaGenerator(builder).generate()

        val modelDir = targetFolder.resolve("model")
        assertThat(modelDir.exists()).isTrue()
        assertThat(modelDir.isDirectory()).isTrue()

        val actualDirs = modelDir.listFiles()
                ?.filter { it.isDirectory() }
                ?.map { it.name }
                ?.toSet() ?: emptySet()

        assertThat(actualDirs).each {
            it.isFromAllowedModules(entryPointModules + additionalModules)
        }
    }

    @Test
    fun `generated navigation html should contains all definitions from included modules only`() {
        val entryPointModules = listOf("main")
        val additionalModules = listOf("lib.lib1")

        val moduleDeclarations = mapOf(
                "main" to listOf(
                        "person",
                        "set_name",
                        "hello_world",
                        "my_name",
                ),
                "lib.lib1" to listOf(
                        "color",
                        "user",
                        "get_message",
                        "create_user",
                        "get_all_users"
                ),
                "lib.lib1.nested" to listOf(
                        "product",
                        "get_product_info"
                )
        )

        val builder = configBuilder(
                projectRoot.resolve("src"),
                entryPointModules,
                additionalModules
        ).targetFolder(targetFolder)

        RellDokkaGenerator(builder).generate()

        val navigationHtmlFile = File(targetFolder, "navigation.html")
        val htmlContent = navigationHtmlFile.readText()

        val regex = Regex("""<a href="model/([^"]+)" class="toc--link"""")
        val result = regex.findAll(htmlContent)
                .mapNotNull { match -> match.groupValues.getOrNull(1) }
                .mapNotNull { path ->
                    val withoutExtension = when {
                        path.endsWith("/index.html") -> path.substringBefore("/index.html")
                        path.endsWith(".html") -> path.substringBeforeLast(".html")
                        else -> path
                    }

                    val withoutHyphenated = if (withoutExtension.contains("-")) {
                        withoutExtension.substringBefore("-")
                    } else {
                        withoutExtension
                    }

                    val trimmed = withoutHyphenated.trimEnd('/')
                    val moduleName = trimmed.substringBeforeLast("/", trimmed)
                    val declarationName = trimmed.substringAfterLast("/", trimmed)

                    if (moduleName == declarationName) null
                    else moduleName to declarationName
                }
                .distinct()
                .groupBy(keySelector = { (parent, _) -> parent }, valueTransform = { (_, child) -> child })

        moduleDeclarations.forEach { (key, expectedList) ->
            assertThat(result[key])
                    .isNotNull()
                    .containsExactlyInAnyOrder(*expectedList.toTypedArray())
        }

    }

    private fun Assert<String>.isFromAllowedModules(modules: List<String>) {
        given { ns ->
            val isValid = modules.any { ns.startsWith(it) }
            assertThat(isValid).isTrue()
        }
    }

    private fun configBuilder(
            projectRoot: File,
            modules: List<String>,
            additionalModules: List<String> = emptyList()
    ): RellDokkaPluginConfigurationBuilder {
        return RellDokkaPluginConfigurationBuilder(
                title = "model",
                modules = modules,
                projectRoot = projectRoot
        )
                .customStyleSheets(listOf())
                .customAssets(listOf())
                .includes(listOf())
                .footerMessage("Footer Message")
                .filteredModules(listOf())
                .additionalModules(additionalModules)
    }
}