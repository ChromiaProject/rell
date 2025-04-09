package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File

class RellIndexingManagerTest : WorkspaceManagerTestBase() {

    private val rellFilePath = "rell_file.rell"
    private val rellFileContent = """
                module;
                function main() {
                    return "main";
                }
    """.trimIndent()

    @Test
    fun `Index multi project workspace`() {
        val firstProjectBuilder = testData(workspace.resolve("first_project")) {
            addFile(rellFilePath, rellFileContent)
        }
        val secondProjectBuilder = testData(workspace.resolve("second_project")) {
            addFile(rellFilePath, rellFileContent)
        }
        val thirdProjectBuilder = testData(workspace.resolve("third_project")) {
            addFile(rellFilePath, rellFileContent)
        }

        initializeWorkspace()
        val indexers = indexingManager.indexers

        assertThat(indexers).hasSize(3)
        assertThat(indexers.keys).containsExactlyInAnyOrder(
            firstProjectBuilder.sourceFolderUri,
            secondProjectBuilder.sourceFolderUri,
            thirdProjectBuilder.sourceFolderUri
        )
        assertThat(
            indexers[firstProjectBuilder.sourceFolderUri]!!.fileUriResourceMap.keys
        ).containsOnly(firstProjectBuilder.sourceFile(rellFilePath).toURI())
        assertThat(
            indexers[secondProjectBuilder.sourceFolderUri]!!.fileUriResourceMap.keys
        ).containsOnly(secondProjectBuilder.sourceFile(rellFilePath).toURI())
        assertThat(
            indexers[thirdProjectBuilder.sourceFolderUri]!!.fileUriResourceMap.keys
        ).containsOnly(thirdProjectBuilder.sourceFile(rellFilePath).toURI())
    }

    @Test
    fun `Index orphan rell files (without chromia yml config)`() {
        testData(workspace.resolve("with_config")) {
            addFile(rellFilePath, rellFileContent)
        }
        val orphanDir = workspace.resolve("orphan_rell")
        orphanDir.mkdirs()
        orphanDir.resolve("orphan.rell").writeText(rellFileContent)

        initializeWorkspace()
        val indexers = indexingManager.indexers

        assertThat(indexers).hasSize(1)
        assertThat(indexingManager.orphanIndexers).hasSize(1)
    }

    @Test
    fun `File URI located in dot git folder is skipped by indexer`() {
        val filePath = ".git/$rellFilePath"
        val testDataBuilder = testData(workspace) {
            addFile(filePath, rellFileContent)
        }
        val rellFileUri = testDataBuilder.sourceFile(filePath).toURI()

        initializeWorkspace()

        workspaceManager.didOpen(rellFileUri, 1, "module;")

        val indexer = indexingManager.indexers[sourceDir.toURI()]!!
        val resource = indexer.fileUriResourceMap[rellFileUri]
        assertThat(resource).isNull()
        assertThat(indexer.fileUriResourceMap.keys).isEmpty()
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(rellFileUri)
    }

    @Test
    fun `Resolves source roots from chromia config file`() {
        val customSourceFolder = "custom_src"
        val testDataBuilder = testData(workspace) {
            addWorkspaceFile("$customSourceFolder/$rellFilePath", rellFileContent)
            config {
                blockchains(
                    """
                    blockchains:
                      rellDappWithLib:
                        module: main
                    compile:
                      rellVersion: 0.14.1
                      source: $customSourceFolder
                    """.trimIndent()
                )
            }
        }

        initializeWorkspace()

        val indexers = indexingManager.indexers
        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys).containsOnly(testDataBuilder.workspaceFolder.resolve(customSourceFolder).toURI())
    }

    @Test
    fun `Index rell files located not in conventional folders relative to chromia yml`() {
        val customSourceFolder = workspace.resolve("custom_folder")
        customSourceFolder.mkdirs()
        customSourceFolder.resolve("chromia.yml").writeText("")
        customSourceFolder.resolve(rellFilePath).writeText(rellFileContent)

        initializeWorkspace()

        val indexers = indexingManager.indexers
        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys).containsOnly(customSourceFolder.toURI())
    }

    @Test
    fun `Collects all orphan files across workspace into single orphan indexer`() {
        val testDataBuilder = testData(workspace) {
            addWorkspaceFile("orphan1/test.rell", rellFileContent)
            addWorkspaceFile("orphan2/other.rell", rellFileContent)
            addMainFile(rellFileContent)
        }

        initializeWorkspace()

        val orphanIndexers = indexingManager.orphanIndexers
        assertThat(orphanIndexers).hasSize(1)
        val orphanIndexer = orphanIndexers.values.first()
        assertThat(orphanIndexer.fileUriResourceMap.keys).containsExactlyInAnyOrder(
            testDataBuilder.workspaceFolder.resolve("orphan1/test.rell").toURI(),
            testDataBuilder.workspaceFolder.resolve("orphan2/other.rell").toURI()
        )
    }

    @Test
    fun `Updates indexer when module folder is renamed`() {
        val oldPath = "module1"
        val newPath = "module2"
        val testDataBuilder = testData(workspace) {
            addFile("$oldPath/test.rell", rellFileContent)
        }

        initializeWorkspace()

        val oldUri = testDataBuilder.sourceFolder.resolve(oldPath).toURI()
        val newUri = testDataBuilder.sourceFolder.resolve(newPath).toURI()

        assertThat(indexingManager.indexers.keys).containsOnly(testDataBuilder.sourceFolderUri)
        val oldIndexer = indexingManager.indexers.values.first()
        assertThat(oldIndexer.fileUriResourceMap).hasSize(1)
        val oldResourceFileUri = oldIndexer.fileUriResourceMap.keys.first()
        assertThat(oldResourceFileUri.startsWith(oldUri)).isTrue()

        File(oldUri).renameTo(File(newUri))
        indexingManager.handleFolderChanges(dirtyFolders = listOf(newUri), deletedFolders = listOf(oldUri))

        assertThat(indexingManager.indexers.keys).containsOnly(testDataBuilder.sourceFolderUri)
        val newIndexer = indexingManager.indexers.values.first()
        assertThat(newIndexer.fileUriResourceMap).hasSize(1)
        val newResourceFileUri = newIndexer.fileUriResourceMap.keys.first()
        assertThat(newResourceFileUri.startsWith(newUri)).isTrue()
    }

    @Test
    fun `Handles nested Rell projects correctly`() {
        val testDataBuilder = testData(workspace) {
            addMainFile(rellFileContent)
            addFile("child/chromia.yml", "")
            addFile("child/src/test.rell", rellFileContent)
        }
        assertThat(notifications).isEmpty()

        initializeWorkspace()

        assertThat(notifications).hasSize(1)
        val indexers = indexingManager.indexers
        assertThat(indexers).hasSize(2)
        assertThat(indexers.keys).containsExactlyInAnyOrder(
            testDataBuilder.sourceFolderUri,
            testDataBuilder.sourceFolder.resolve("child/src").toURI()
        )
    }

    @Test
    fun `Updates indexers when project folder is renamed`() {
        val oldPath = "project1"
        val newPath = "project2"
        val testDataBuilder = testData(workspace) {
            addWorkspaceFile("$oldPath/chromia.yml", "")
            addWorkspaceFile("$oldPath/src/test.rell", rellFileContent)
        }

        initializeWorkspace()

        val oldUri = testDataBuilder.workspaceFolder.resolve(oldPath).toURI()
        val newUri = testDataBuilder.workspaceFolder.resolve(newPath).toURI()

        assertThat(indexingManager.indexers.size).isEqualTo(2)
        val oldIndexer = indexingManager.indexers[oldUri.resolve("src/")]
        assertThat(oldIndexer).isNotNull()
        assertThat(oldIndexer?.fileUriResourceMap!!.keys).containsOnly(
            testDataBuilder.workspaceFolder.resolve("$oldPath/src/test.rell").toURI()
        )

        File(oldUri).renameTo(File(newUri))
        indexingManager.handleFolderChanges(dirtyFolders = listOf(newUri), deletedFolders = listOf(oldUri))

        assertThat(indexingManager.indexers.size).isEqualTo(2)
        val newSourcePath = testDataBuilder.workspaceFolder.resolve("$newPath/src").toURI()
        val newIndexer = indexingManager.indexers[newSourcePath]
        assertThat(newIndexer).isNotNull()
        assertThat(newIndexer?.fileUriResourceMap!!.keys).containsOnly(
            testDataBuilder.workspaceFolder.resolve("$newPath/src/test.rell").toURI()
        )
        assertThat(indexingManager.indexers[oldUri.resolve("src/")]).isNull()
    }

    @Test
    fun `Handles folders with same prefix correctly during rename`() {
        val testDataBuilder = testData(workspace) {
            addWorkspaceFile("proj/chromia.yml", "")
            addWorkspaceFile("proj/src/test.rell", rellFileContent)
            addWorkspaceFile("proj_old/chromia.yml", "")
            addWorkspaceFile("proj_old/src/other.rell", rellFileContent)
        }

        initializeWorkspace()

        val oldUri = testDataBuilder.workspaceFolder.resolve("proj_old").toURI()
        val otherProjUri = testDataBuilder.workspaceFolder.resolve("proj").toURI()

        assertThat(indexingManager.indexers.keys).containsExactlyInAnyOrder(
            oldUri.resolve("src/"),
            otherProjUri.resolve("src/"),
            testDataBuilder.sourceFolderUri
        )

        val newUri = testDataBuilder.workspaceFolder.resolve("proj_new").toURI()

        File(oldUri).renameTo(File(newUri))
        indexingManager.handleFolderChanges(dirtyFolders = listOf(newUri), deletedFolders = listOf(oldUri))

        val newSourceUri = testDataBuilder.workspaceFolder.resolve("proj_new/src/").toURI()
        assertThat(indexingManager.indexers.keys).containsExactlyInAnyOrder(
            newSourceUri,
            otherProjUri.resolve("src/"),
            testDataBuilder.sourceFolderUri
        )

        assertThat(indexingManager.indexers[newSourceUri]!!.fileUriResourceMap.keys).containsOnly(
            testDataBuilder.workspaceFolder.resolve("proj_new/src/other.rell").toURI()
        )
    }

    @Test
    fun `Creates new indexer for new project without duplicating files`() {
        val initialProject = testData(workspace.resolve("project_a")) {
            addWorkspaceFile("orphan/test.rell", rellFileContent)
            addMainFile(rellFileContent)
        }

        initializeWorkspace()

        val orphanUri = initialProject.workspaceFolder.resolve("orphan/test.rell").toURI()
        val orphanIndexer = indexingManager.orphanIndexers.values.first()
        assertThat(indexingManager.indexers.size).isEqualTo(1)
        assertThat(indexingManager.indexers[initialProject.sourceFolderUri]!!.fileUriResourceMap.keys).containsOnly(
            initialProject.mainFileUri
        )
        assertThat(indexingManager.orphanIndexers.size).isEqualTo(1)
        assertThat(orphanIndexer.fileUriResourceMap.keys).containsExactlyInAnyOrder(orphanUri)

        val secondProject = testData(workspace.resolve("project_b")) {
            addMainFile(rellFileContent)
        }

        indexingManager.indexFromRoots(listOf(secondProject.chromiaConfigFileUri))

        assertThat(indexingManager.orphanIndexers.size).isEqualTo(1)
        assertThat(orphanIndexer.fileUriResourceMap.keys).containsExactlyInAnyOrder(orphanUri)
        assertThat(indexingManager.indexers.keys).containsExactlyInAnyOrder(
            initialProject.sourceFolderUri,
            secondProject.sourceFolderUri
        )
        assertThat(indexingManager.indexers[initialProject.sourceFolderUri]!!.fileUriResourceMap.keys).containsOnly(
            initialProject.mainFileUri
        )
        assertThat(indexingManager.indexers[secondProject.sourceFolderUri]!!.fileUriResourceMap.keys).containsOnly(
            secondProject.mainFileUri
        )
    }

    @Test
    fun `findAffectedFiles does not throw NPE when file is not in indexer's fileUriResourceMap`() {
        // Setup initial project with a file
        val initialProject = testData(workspace.resolve("project_a")) {
            addMainFile(rellFileContent)
        }

        initializeWorkspace()

        // Create a new file outside the source directory but within project root
        val outsideFile = initialProject.workspaceFolder.resolve("outside.rell")
        outsideFile.writeText("module;")
        val outsideFileUri = outsideFile.toURI()

        val indexer = indexingManager.getIndexerFor(outsideFileUri)

        assertThat(indexer.workspaceUri).isEqualTo(outsideFileUri)
        assertThat(indexer.fileUriResourceMap.keys).contains(outsideFileUri)

        assertDoesNotThrow { indexer.findAffectedFiles(outsideFileUri) }
    }

    @Test
    fun `handleFileChange handles non-existing dirty file`() {
        val testData = testData(workspace) {
            addMainFile(rellFileContent)
        }

        initializeWorkspace()
        val nonExistingDirtyFile = testData.workspaceFolder.resolve("not_on_disk.rell").toURI()
        val indexer = indexingManager.getIndexerFor(nonExistingDirtyFile)

        assertDoesNotThrow { indexingManager.handleFileChanges(listOf(nonExistingDirtyFile), listOf(), true) }
        assertThat(indexer.fileUriResourceMap[nonExistingDirtyFile]).isNull()
    }
}
