package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.toolbox.core.compiler.RellcAPI
import net.postchain.rell.toolbox.core.compiler.RellcFilePath
import net.postchain.rell.toolbox.core.parser.RellParser
import java.net.URI

data class Resource(val uri: URI,  val workspaceURI: URI,  val parseTree: RellParser.RuleX_RootParserContext) {
    val relativePath = uri.toString().substring(workspaceURI.toString().length)
    val compilerSrcPath = IdeDirApi.parseSourcePath(relativePath)
    val idePath = IdeSourcePathFilePath(compilerSrcPath!!)
    val rcPath = RellcFilePath(compilerSrcPath!!, idePath)
    val ast = RellcAPI.antlrToRellAst(rcPath, parseTree)
    val moduleInfo = ast.first!!.ideModuleInfo(compilerSrcPath!!)
    val rName = moduleInfo!!.name
    val imports = moduleInfo?.imports
}