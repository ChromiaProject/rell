/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.indexer.findRellFilesInWorkspace
import java.io.File
import java.net.URI

class FileChangeHandler(
    private val diagnosticsManager: RellDiagnosticsManager,
    private val indexerRegistry: IndexerRegistry,
) {

    fun handleFileChanges(
        dirtyFiles: List<URI>,
        deletedFiles: List<URI>,
        updateAffectedFiles: Boolean
    ): List<URI> {
        val affectedUris = mutableSetOf<URI>()

        handleDeletedFiles(deletedFiles, updateAffectedFiles, affectedUris)
        handleDirtyFiles(dirtyFiles, updateAffectedFiles, affectedUris)

        affectedUris.removeAll((deletedFiles + dirtyFiles).toSet())
        updateAffectedFiles(affectedUris)

        val allUris = (dirtyFiles + deletedFiles + affectedUris)
        reportDiagnostics(allUris)
        return allUris
    }

    fun handleFolderChanges(
        dirtyFolders: List<URI>,
        deletedFolders: List<URI>,
    ) {
        if (isFolderRename(dirtyFolders, deletedFolders)) {
            handleFolderRename(dirtyFolders, deletedFolders)
        } else {
            syncFileAndFolderState(dirtyFolders, deletedFolders)
        }
    }

    private fun isFolderRename(dirtyFolders: List<URI>, deletedFolders: List<URI>): Boolean {
        return dirtyFolders.size == 1 && deletedFolders.size == 1
    }

    private fun handleFolderRename(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        val deletedFolderUri = deletedFolders.firstOrNull() ?: return
        val newFolderUri = dirtyFolders.firstOrNull() ?: return

        indexerRegistry.getIndexerForFolderOrNull(deletedFolderUri)?.let { indexer ->
            if (deletedFolderIsIndexerRoot(indexer, deletedFolderUri)) {
                folderRenameOnIndexerProjectRoot(indexer, newFolderUri)
            } else {
                syncFileAndFolderState(dirtyFolders, deletedFolders)
            }
        }
    }

    private fun deletedFolderIsIndexerRoot(indexer: WorkspaceIndexer, deletedFolderUri: URI) =
        indexer.projectRootUri?.path?.trimEnd('/') == deletedFolderUri.path.trimEnd('/')

    private fun folderRenameOnIndexerProjectRoot(indexer: WorkspaceIndexer, newFolderUri: URI) {
        val indexRoots = IndexRoot.findIndexRoots(newFolderUri)

        val newIndexers = if (indexRoots.isEmpty()) {
            listOf(indexerRegistry.doIndex(WorkspaceDirectoryResolver.findSourceDirURI(newFolderUri), newFolderUri))
        } else {
            indexRoots.map { indexRoot ->
                indexerRegistry.doIndex(indexRoot.sourceRootUri, indexRoot.chromiaConfigDirUri)
            }
        }.associateBy { it.workspaceUri }

        val indexers = indexerRegistry.getAllIndexersMap()
        indexers.putAll(newIndexers)
        indexers.remove(indexer.workspaceUri)
        indexerRegistry.cleanUpOrphans(newIndexers)
    }

    private fun syncFileAndFolderState(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        val deletedFiles = mutableListOf<URI>()
        val dirtyFiles = mutableListOf<URI>()

        deletedFolders.forEach { uri ->
            indexerRegistry.getIndexerForOrNull(uri)?.let { indexer ->
                deletedFiles.addAll(indexer.getFileUrisWithPrefix(uri))
            }
        }

        dirtyFolders.forEach { uri ->
            findRellFilesInWorkspace(File(uri), dirtyFiles)
        }

        // Need to do two passes of the files to guarantee that imports and modules are resolved correctly
        dirtyFiles.addAll(dirtyFiles)
        handleFileChanges(dirtyFiles, deletedFiles, true)
    }

    private fun handleDeletedFiles(
        deletedFiles: List<URI>,
        updateAffectedFiles: Boolean,
        affectedUris: MutableSet<URI>
    ) {
        deletedFiles.forEach { uri ->
            indexerRegistry.getIndexerFor(uri).let { indexer ->
                if (updateAffectedFiles) {
                    affectedUris.addAll(indexer.findAffectedFiles(uri))
                }
                indexer.removeFileUriResourceMap(uri)
                diagnosticsManager.clearDiagnostics(uri)
            }
        }
    }

    private fun handleDirtyFiles(
        dirtyFiles: List<URI>,
        updateAffectedFiles: Boolean,
        affectedUris: MutableSet<URI>
    ) {
        dirtyFiles.forEach { uri ->
            indexerRegistry.getIndexerFor(uri).let { indexer ->
                val updatedResource = indexer.updateFileUriResourceMap(uri)
                if (updateAffectedFiles && updatedResource != null) {
                    affectedUris.addAll(indexer.findAffectedFiles(uri))
                }
            }
        }
    }

    private fun updateAffectedFiles(affectedUris: Set<URI>) {
        affectedUris.forEach { uri ->
            indexerRegistry.getIndexerFor(uri).updateFileUriResourceMap(uri)
        }
    }

    private fun reportDiagnostics(fileUris: List<URI>) {
        if (fileUris.isNotEmpty()) {
            for (indexer in indexerRegistry.getAllIndexersMap().values) {
                diagnosticsManager.reportDiagnostics(indexer, fileUris)
            }
        }
    }
}
