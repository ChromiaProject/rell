package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.toolbox.core.compiler.RellcAPI
import net.postchain.rell.toolbox.core.compiler.RellcFilePath
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import java.io.File
import java.net.URI

class RellResourceDescription {
    var fileUriModuleInfoMap: HashMap<URI, IdeModuleInfo> = HashMap()

    fun buildRellResource(rootURI: URI) {
        val parser = AntlrRellParser()
        val rellUris = WorkspaceIndexer().addRellFilesUri(rootURI)

        rellUris.forEach { uri ->
            val antlrRellRootNode = parser.parse(File(uri).readText())

            val testPath = uri.toString().substring("file:/".length)
            val compilerSrcPath = IdeDirApi.parseSourcePath(testPath)
            val idePath = IdeSourcePathFilePath(compilerSrcPath!!)
            val rcPath = RellcFilePath(compilerSrcPath, idePath)

            val ast = RellcAPI.antlrToRellAst(rcPath, antlrRellRootNode)

            val moduleInfo = ast.first!!.ideModuleInfo(compilerSrcPath)
//            val moduleInfo = IdeApi.getModuleInfo(compilerSrcPath, ast.first!!)
            fileUriModuleInfoMap[uri] = moduleInfo!!

        }


    }
}