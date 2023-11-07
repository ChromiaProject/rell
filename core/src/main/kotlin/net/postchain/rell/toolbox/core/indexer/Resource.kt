package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError

data class Resource(
    val parseTree: RellParser.RuleX_RootParserContext,
    val moduleInfo: IdeModuleInfo?,
    //val cSourceFile: C_SourceFile,
    val ast: S_RellFile,
    val syntaxErrors: List<SyntaxError> = listOf(),
    val semanticErrors: List<C_Message> = listOf()
) {
    val rName = moduleInfo?.name
    val imports = moduleInfo?.imports ?: listOf()
    //val idePath: IdeFilePath = cSourceFile.idePath()
}
