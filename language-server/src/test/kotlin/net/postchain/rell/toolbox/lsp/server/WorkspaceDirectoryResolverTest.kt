package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.file.Path

class WorkspaceDirectoryResolverTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `findSourceDirURI returns rell-src folder when it exists`() {
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("rell/src/main.rell", "module;")
        }
        testDataBuilder.sourceFolder.deleteRecursively()

        val sourceUri = WorkspaceDirectoryResolver.findSourceDirURI(tempDir.toURI())
        val expectedPath = testDataBuilder.workspaceFolder.resolve("rell/src").toURI()

        assertThat(sourceUri).isEqualTo(expectedPath)
    }

    @Test
    fun `findSourceDirURI returns rell folder when it exists without src`() {
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("rell/main.rell", "module;")
        }
        testDataBuilder.sourceFolder.deleteRecursively()

        val sourceUri = WorkspaceDirectoryResolver.findSourceDirURI(tempDir.toURI())
        val expectedPath = testDataBuilder.workspaceFolder.resolve("rell").toURI()

        assertThat(sourceUri).isEqualTo(expectedPath)
    }

    @Test
    fun `findSourceDirURI returns src folder when it exists without rell`() {
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("src/main.rell", "module;")
        }

        val sourceUri = WorkspaceDirectoryResolver.findSourceDirURI(tempDir.toURI())
        val expectedPath = testDataBuilder.workspaceFolder.resolve("src").toURI()

        assertThat(sourceUri).isEqualTo(expectedPath)
    }

    @Test
    fun `findSourceDirURI returns workspace folder when no standard source folders exist`() {
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("main.rell", "module;")
        }
        testDataBuilder.sourceFolder.deleteRecursively()

        val sourceUri = WorkspaceDirectoryResolver.findSourceDirURI(tempDir.toURI())
        val expectedPath = testDataBuilder.workspaceFolder.toURI()

        assertThat(sourceUri).isEqualTo(expectedPath)
    }

    @Test
    fun `findSourceRootPath returns path from chromia model when it exists`() {
        val customSourcePath = "custom/src"
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("$customSourcePath/main.rell", "module;")
            config {
                compile(
                    """
                    compile:
                        source: $customSourcePath
                    """.trimIndent()
                )
            }
        }

        val sourcePath = WorkspaceDirectoryResolver.findSourceRootPath(testDataBuilder.chromiaConfigFile.toPath())
        val expectedPath = testDataBuilder.workspaceFolder.resolve(customSourcePath).toPath().normalize()

        assertThat(sourcePath).isEqualTo(expectedPath)
    }

    @Test
    fun `findSourceRootPath falls back to source directory when not specified in config`() {
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("src/main.rell", "module;")
            config {
                compile(
                    """
                    compile:
                        rellVersion: 0.14.0
                    """.trimIndent()
                )
            }
        }

        val sourcePath = WorkspaceDirectoryResolver.findSourceRootPath(testDataBuilder.chromiaConfigFile.toPath())
        val expectedPath = testDataBuilder.workspaceFolder.resolve("src").toPath()

        assertThat(sourcePath).isEqualTo(expectedPath)
    }

    @Test
    fun `findProjectRootURI returns parent directory when chromia config exists there`() {
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("src/main.rell", "module;")
        }

        val projectRootUri = WorkspaceDirectoryResolver.findProjectRootURI(testDataBuilder.sourceFolderUri)
        val expectedUri = testDataBuilder.workspaceFolder.toURI()

        assertThat(projectRootUri).isEqualTo(expectedUri)
    }

    @Test
    fun `findProjectRootURI returns grandparent directory when chromia config exists there`() {
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("project/src/main.rell", "module;")
            addWorkspaceFile("chromia.yml", "")
        }

        val sourceUri = testDataBuilder.workspaceFolder.resolve("project/src").toURI()
        val projectRootUri = WorkspaceDirectoryResolver.findProjectRootURI(sourceUri)
        val expectedUri = testDataBuilder.workspaceFolder.toURI()

        assertThat(projectRootUri).isEqualTo(expectedUri)
    }

    @Test
    fun `findProjectRootURI returns null when no chromia config found`() {
        val testDataBuilder = testData(tempDir) {
            addWorkspaceFile("src/main.rell", "module;")
        }
        testDataBuilder.chromiaConfigFile.delete()

        val projectRootUri = WorkspaceDirectoryResolver.findProjectRootURI(testDataBuilder.sourceFolderUri)

        assertThat(projectRootUri).isEqualTo(null)
    }
}
