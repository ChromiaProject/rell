package net.postchain.rell.toolbox.lsp.caching

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.parser.AbstractRellCommonTokenStream
import net.postchain.rell.toolbox.parser.SyntaxError
import org.antlr.v4.runtime.ParserRuleContext
import java.net.URI

data class SerializableResource(
    val parseTree: ParserRuleContext,
    val moduleInfo: IdeModuleInfo?,
    val fileUri: URI,
    val workspaceUri: URI,
    val ast: S_RellFile,
    val syntaxErrors: List<SyntaxError> = listOf(),
    val semanticErrors: List<C_Message> = listOf(),
    val symbolInfos: Map<S_Pos, SerializableSymbolInfo>,
    val checksum: String,
    val tokenStream: AbstractRellCommonTokenStream,
    val linterIssues: List<LinterIssue> = listOf(),
    val formatterIssues: List<FormatterIssue> = listOf()
)
