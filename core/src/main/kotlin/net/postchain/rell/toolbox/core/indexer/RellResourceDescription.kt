package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.toolbox.core.compiler.RellcAPI
import net.postchain.rell.toolbox.core.compiler.RellcFilePath
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import java.io.File
import java.net.URI

//TODO rename placeholder
data class placeholder (val moduleInfo: IdeModuleInfo, val relativePath: String, val absolutePath: String) {

}

class RellResourceDescription {
    var fileUriModuleInfoMap: HashMap<URI, placeholder> = HashMap()

    fun buildRellResource(rootURI: URI) {
        val parser = AntlrRellParser()
        val rellUris = WorkspaceIndexer().addRellFilesUri(rootURI)

        rellUris.forEach { uri ->
            //TODO verfiy correct path behaviour
            val antlrRellRootNode = parser.parse(File(uri).readText())
            val relativePath = uri.toString().substring(rootURI.toString().length)
            val compilerSrcPath = IdeDirApi.parseSourcePath(relativePath)
            val idePath = IdeSourcePathFilePath(compilerSrcPath!!)
            val rcPath = RellcFilePath(compilerSrcPath, idePath)

            val ast = RellcAPI.antlrToRellAst(rcPath, antlrRellRootNode)

            val moduleInfo = ast.first!!.ideModuleInfo(compilerSrcPath)
            fileUriModuleInfoMap[uri] = placeholder(moduleInfo!!, relativePath, uri.toString())
        }


    }
}