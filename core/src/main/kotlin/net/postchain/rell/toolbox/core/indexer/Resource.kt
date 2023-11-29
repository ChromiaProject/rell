package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError
import org.antlr.v4.runtime.misc.Interval
import java.net.URI

data class Resource(
    val parseTree: RellParser.RuleX_RootParserContext,
    val moduleInfo: IdeModuleInfo?,
    val fileUri: URI,
    val workspaceUri: URI,
    val ast: S_RellFile,
    val syntaxErrors: List<SyntaxError> = listOf(),
    val semanticErrors: List<C_Message> = listOf(),
    val symbolInfos: Map<S_Pos, IdeSymbolInfo>,
    val locationInfo: Map<Interval, IdeSymbolInfoWithInterval>
) {
    fun getSymbolKindForInterval(interval: Interval): IdeSymbolKind? {
        val symbolInfoWithInterval = locationInfo[interval]
        return symbolInfoWithInterval?.ideSymbolInfo?.kind
    }

    val rName = moduleInfo?.name
    val imports = moduleInfo?.imports ?: listOf()
    val fileSpecificSemanticErrors: List<C_Message> = semanticErrors.filter {
        val resourceFullPath = workspaceUri.path + it.pos.path().str()
        fileUri.path == resourceFullPath
    }
}
