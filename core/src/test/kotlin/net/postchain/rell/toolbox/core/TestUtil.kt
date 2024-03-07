package net.postchain.rell.toolbox.core

import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.core.indexer.RellResourceFactory
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.findRellFilesInWorkspace
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import java.io.File
import java.net.URI

class TestUtil {
    fun createTestResource(fileSuffix: String, resourceFolders: String): Resource {
        val classLoader = javaClass.getClassLoader()
        var rellFilesErrors: MutableList<URI> = mutableListOf()
        val workspaceError = File(classLoader.getResource(resourceFolders).file)
        findRellFilesInWorkspace(
            workspaceError,
            rellFilesErrors
        )

        val fileUri = rellFilesErrors.find { it.toString().endsWith(fileSuffix) }!!
        val rellDesc = RellResourceFactory(workspaceError.toURI(), AntlrRellParser())
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        return rellDesc.buildRellResource(fileUri, fileMap)
    }
}
