package net.postchain.rell.toolbox.chromia

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ChromiaModelProviderTest {


    @Test
    fun `resolveIgnoreReportingUris should return set of path to external libraries`(@TempDir dir: File) {
        val srcDir = File(dir, "src")
        srcDir.mkdirs()

        val externalLibName = "external"

        val internalLib = File(srcDir, "lib/internal/module.rell").apply {
            parentFile.mkdirs()
        }.toURI()

        val externalLib = File(srcDir, "lib/$externalLibName/module.rell").apply {
            parentFile.mkdirs()
        }

        File(dir, "chromia.yml").apply {
            writeText(
                """
                blockchains:
                  rellDappWithLib:
                    module: main

                libs:
                  $externalLibName:
                    registry: a.registry.abc
                    path: a/path/to/registry
            """.trimIndent()
            )
        }

        val ignoreReportingUris = ChromiaModelProvider().resolveIgnoreReportingUris(dir.toURI(), srcDir.toURI())
        assertThat(ignoreReportingUris).containsOnly(externalLib.parentFile.toURI())
    }

    @Test
    fun `resolveIgnoreReportingUris should return empty set when no libs defined in chromia yml`(@TempDir dir: File) {
        val srcDir = File(dir, "src")
        srcDir.mkdirs()

        val externalLibName = "external"

        val internalLib = File(srcDir, "lib/internal/module.rell").apply {
            parentFile.mkdirs()
        }.toURI()

        val externalLib = File(srcDir, "lib/$externalLibName/module.rell").apply {
            parentFile.mkdirs()
        }

        File(dir, "chromia.yml").apply {
            writeText(
                """
                blockchains:
                  rellDappWithLib:
                    module: main
            """.trimIndent()
            )
        }

        val ignoreReportingUris = ChromiaModelProvider().resolveIgnoreReportingUris(dir.toURI(), srcDir.toURI())
        assertThat(ignoreReportingUris).isEmpty()
    }

    @Test
    fun `resolveIgnoreReportingUris should return empty set when chromia yml format is faulty`(@TempDir dir: File) {
        val srcDir = File(dir, "src")
        srcDir.mkdirs()

        val externalLibName = "external"

        val internalLib = File(srcDir, "lib/internal/module.rell").apply {
            parentFile.mkdirs()
        }.toURI()

        val externalLib = File(srcDir, "lib/$externalLibName/module.rell").apply {
            parentFile.mkdirs()
        }

        File(dir, "chromia.yml").apply {
            writeText(
                """
                libs:
                  $externalLibName:
                    registry: a.registry.abc
                    path: a/path/to/registry
            """.trimIndent()
            )
        }

        val ignoreReportingUris = ChromiaModelProvider().resolveIgnoreReportingUris(dir.toURI(), srcDir.toURI())
        assertThat(ignoreReportingUris).isEmpty()
    }

    @Test
    fun `resolveIgnoreReportingUris should return empty set when workspaceRootUri is not passed to call`(@TempDir dir: File) {
        val srcDir = File(dir, "src")
        srcDir.mkdirs()
        
        val ignoreReportingUris = ChromiaModelProvider().resolveIgnoreReportingUris(null, srcDir.toURI())
        assertThat(ignoreReportingUris).isEmpty()
    }
}
