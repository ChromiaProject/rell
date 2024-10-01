package net.postchain.rell.toolbox.chromia

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.chromia.cli.model.RellLibraryModel
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ChromiaModelProviderTest {
    private val externalLibName = "external"
    private val externalLibModulePath = "lib/$externalLibName/module.rell"
    private val internalLibModulePath = "lib/internal/module.rell"

    @Test
    fun `resolveIgnoreReportingUris should return set of path to external libraries`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            emptyModule(internalLibModulePath)
            emptyModule(externalLibModulePath)
            config {
                blockchains(
                    """
                    blockchains:
                        rellDappWithLib:
                            module: main
                    """.trimIndent()
                )
                addLib(externalLibName, RellLibraryModel("a.registry.abc", null, "a/path/to/registry", false, null))
            }
        }

        val ignoreReportingUris = ChromiaModelProvider(
            dir.toURI()
        ).resolveIgnoreReportingUris(testDataBuilder.sourceFolderURI)
        assertThat(ignoreReportingUris).containsOnly(
            testDataBuilder.sourceFolder.resolve(externalLibModulePath).parentFile.toURI()
        )
    }

    @Test
    fun `resolveIgnoreReportingUris should return empty set when no libs defined in chromia yml`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            emptyModule(internalLibModulePath)
            emptyModule(externalLibModulePath)
            config {
                blockchains(
                    """
                    blockchains:
                        rellDappWithLib:
                            module: main
                    """.trimIndent()
                )
            }
        }

        val ignoreReportingUris = ChromiaModelProvider(
            dir.toURI()
        ).resolveIgnoreReportingUris(testDataBuilder.sourceFolderURI)
        assertThat(ignoreReportingUris).isEmpty()
    }

    @Test
    fun `resolveIgnoreReportingUris should return empty set when chromia yml format is faulty`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            emptyModule(internalLibModulePath)
            emptyModule(externalLibModulePath)
            config {
                blockchains(
                    """
                    libs:
                      $externalLibName:
                        registry: a.registry.abc
                        path: a/path/to/registry
                    """.trimIndent()
                )
                addLib(externalLibName, RellLibraryModel("a.registry.abc", null, "a/path/to/registry", false, null))
            }
        }

        val ignoreReportingUris = ChromiaModelProvider(
            dir.toURI()
        ).resolveIgnoreReportingUris(testDataBuilder.sourceFolderURI)
        assertThat(ignoreReportingUris).isEmpty()
    }

    @Test
    fun `resolveIgnoreReportingUris should return empty set when workspaceRootUri is not passed to call`(
        @TempDir dir: File
    ) {
        val testDataBuilder = testData(dir)
        val ignoreReportingUris = ChromiaModelProvider(null).resolveIgnoreReportingUris(testDataBuilder.sourceFolderURI)
        assertThat(ignoreReportingUris).isEmpty()
    }

    @Test
    fun `getRellLanguageVersion should return default version when chromia model is not loaded`() {
        val rellVersion = ChromiaModelProvider(null).getRellLanguageVersion()
        assertThat(rellVersion).isEqualTo(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_RELL_VERSION)
    }

    @Test
    fun `getRellLanguageVersion should return rell version from chromia model`(@TempDir dir: File) {
        testData(dir) {
            config {
                blockchains(
                    """
                    blockchains:
                      rellDappWithLib:
                        module: main
                    compile:
                      rellVersion: 0.13.15
                    """.trimIndent()
                )
            }
        }

        val rellVersion = ChromiaModelProvider(dir.toURI()).getRellLanguageVersion()
        assertThat(rellVersion).isEqualTo("0.13.15")
    }

    @Test
    fun `getRellLanguageVersion should return default version from chromia model when version is empty`(
        @TempDir dir: File
    ) {
        testData(dir) {
            config {
                blockchains(
                    """
                    blockchains:
                      rellDappWithLib:
                        module: main
                    compile:
                      rellVersion: 
                    """.trimIndent()
                )
            }
        }

        val rellVersion = ChromiaModelProvider(dir.toURI()).getRellLanguageVersion()
        assertThat(rellVersion).isEqualTo(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_RELL_VERSION)
    }

    @Test
    fun `getRellLanguageVersion should return default version when compile version isn't specified`(
        @TempDir dir: File
    ) {
        testData(dir) {
            config {
                blockchains(
                    """
                    blockchains:
                      rellDappWithLib:
                        module: main
                    """.trimIndent()
                )
            }
        }

        val rellVersion = ChromiaModelProvider(dir.toURI()).getRellLanguageVersion()
        assertThat(rellVersion).isEqualTo(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_RELL_VERSION)
    }

    @Test
    fun `updateChromiaModel should update chromia model cache`(@TempDir dir: File) {
        testData(dir) {
            config {
                blockchains(
                    """
                    blockchains:
                      rellDappWithLib:
                        module: main
                    """.trimIndent()
                )
            }
        }
        val chromiaModel = ChromiaModelProvider(dir.toURI()).loadChromiaModel()
        val chromiaModelProvider = ChromiaModelProvider(dir.toURI())

        chromiaModelProvider.updateChromiaModel(dir.toURI(), chromiaModel)

        val updatedChromiaModel = chromiaModelProvider.getChromiaModel()
        assertThat(updatedChromiaModel).isEqualTo(chromiaModel)
    }
}
