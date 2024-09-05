package net.postchain.rell.toolbox.indexer

import net.postchain.rell.base.compiler.ast.S_Definition
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.parser.AntlrRellParser
import java.io.File
import java.net.URI
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider

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
        val rellDesc = RellResourceFactory(workspaceError.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        return rellDesc.buildRellResource(fileUri, fileMap)
    }
}


@Suppress("UNCHECKED_CAST")
val S_RellFile.definitionsField: List<S_Definition>
    get() {
        val field = this.javaClass.getDeclaredField("definitions")
        field.isAccessible = true
        return field.get(this) as List<S_Definition>
    }