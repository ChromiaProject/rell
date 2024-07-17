package net.postchain.rell.toolbox.core.indexer

import java.net.URI
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.core.parser.RellCommonTokenStream
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError
import net.postchain.rell.toolbox.linter.FormatterIssue
import net.postchain.rell.toolbox.linter.issues.LinterIssue
import org.antlr.v4.runtime.misc.Interval

data class Resource(
    val parseTree: RellParser.RuleX_RootParserContext,
    val moduleInfo: IdeModuleInfo?,
    val fileUri: URI,
    val workspaceUri: URI,
    val ast: S_RellFile,
    val syntaxErrors: List<SyntaxError> = listOf(),
    val semanticErrors: List<C_Message> = listOf(),
    var linterIssues: List<LinterIssue> = listOf(),
    var formatterIssues: List<FormatterIssue> = listOf(),
    val symbolInfos: Map<S_Pos, IdeSymbolInfo>,
    val userSymbols: Map<IdeSymbolId, S_Pos>,
    val locationInfo: Map<Interval, IdeSymbolInfoWithInterval>,
    val checksum: String? = null,
    val tokenStream: RellCommonTokenStream,
) {
    fun getSymbolKindForInterval(interval: Interval): IdeSymbolKind? {
        val symbolInfoWithInterval = locationInfo[interval]
        return symbolInfoWithInterval?.ideSymbolInfo?.kind
    }

    fun isTest(): Boolean {
        return moduleInfo?.test ?: false
    }

    val rName = moduleInfo?.name
    val imports = moduleInfo?.imports ?: listOf()
    val fileSpecificSemanticErrors: List<C_Message> = semanticErrors.filter {
        val resourceFullPath = workspaceUri.path + it.pos.path().str()
        fileUri.path == resourceFullPath
    }
}
