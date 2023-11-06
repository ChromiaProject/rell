package net.postchain.rell.toolbox.core.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.C_TextSourceFile
import java.io.File
import java.net.URI

//1. init event workspaceFiles[]
// -> loop over each ws and create an WorkspaceIndexer for it
// 2. initialFileIndexBuild (full index)
// 3. update event
// -> updateFileUriResourceMap (simple no imports)
// -> updateFileUriResourceMap + findAffectedFiles (with import)
// -> updateFileUriResourceMap(oldURI, NewURI) file moved
class WorkspaceIndexer(private val workspaceURI: URI) {
    private val logger = KotlinLogging.logger {}
    private val resourceDescription = RellResourceFactory(workspaceURI)

    //TODO: Should we inject this?

    var fileUriResourceMap: HashMap<URI, Resource> = HashMap()
    private val compilerResourceMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
    fun initialFileIndexBuild() {
        val dirtyFiles: MutableMap<URI, String> = mutableMapOf()
        val rellUris = addRellFilesUri()
        rellUris.forEach { fileURI ->
            val fileContent = File(fileURI).readText()
            val compilerSourcePath = RellCompilerPaths(workspaceURI).createCompilerSourcePath(fileURI)
            compilerResourceMap[compilerSourcePath] = C_TextSourceFile(compilerSourcePath, fileContent)
            val resource = resourceDescription.buildRellResource(fileURI, C_SourceDir.mapDir(compilerResourceMap))

            if (resource.imports.isNotEmpty()) {
                dirtyFiles[fileURI] = fileContent
            }
            fileUriResourceMap[fileURI] = resource
        }

        dirtyFiles.forEach { (fileURI, fileContent) ->
            val compilerSourcePath = RellCompilerPaths(workspaceURI).createCompilerSourcePath(fileURI)
            compilerResourceMap[compilerSourcePath] = C_TextSourceFile(compilerSourcePath, fileContent)
            val resource = resourceDescription.buildRellResource(fileURI, C_SourceDir.mapDir(compilerResourceMap))
            fileUriResourceMap[fileURI] = resource
        }

    }

    //Change in source code
    fun updateFileUriResourceMap(fileURI: URI, fileContent: String) {
        val compilerSourcePath = RellCompilerPaths(workspaceURI).createCompilerSourcePath(fileURI)
        compilerResourceMap[compilerSourcePath] = C_TextSourceFile(compilerSourcePath, fileContent)
        fileUriResourceMap[fileURI] =
            resourceDescription.buildRellResource(fileURI, C_SourceDir.mapDir(compilerResourceMap))
    }

    //Rename and Move file
    fun updateFileUriResourceMap(oldUri: URI, newUri: URI) {
        val resource = fileUriResourceMap[oldUri]
        if (resource != null) {
            fileUriResourceMap[newUri] = resource
            fileUriResourceMap.remove(oldUri)
        } else {
            logger.warn { "Could not find resource for $oldUri. Re-parsing file..." }
            val compilerSourcePathNewFile = RellCompilerPaths(workspaceURI).createCompilerSourcePath(newUri)
            val fileContent = File(newUri).readText()
            compilerResourceMap[compilerSourcePathNewFile] = C_TextSourceFile(compilerSourcePathNewFile, fileContent)
            updateFileUriResourceMap(newUri, fileContent)
        }
    }

    fun findAffectedFiles(uri: URI): Set<URI> {
        //TODO make it so it can find affected files that are importing an affected file
        var shallowCopy = fileUriResourceMap.toMutableMap()
        val changedFileResource: Resource = fileUriResourceMap[uri]!!
        shallowCopy.remove(uri)
        var filesToUpdate: MutableSet<URI> = mutableSetOf(uri)

        shallowCopy.forEach { (key, value) ->
            if (value.imports.isNullOrEmpty()) {
                //
            } else {
                if (value.imports.contains(changedFileResource.rName)) {
                    filesToUpdate.add(key)
                }
            }
        }
        return filesToUpdate.toSet()

    }

    fun addRellFilesUri(): List<URI> {
        val uris: MutableList<URI> = ArrayList()
        findRellFilesInWorkspace(File(workspaceURI), uris)
        return uris.toList()
    }
}
