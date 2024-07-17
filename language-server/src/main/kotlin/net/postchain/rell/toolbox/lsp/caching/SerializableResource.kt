package net.postchain.rell.toolbox.lsp.caching

import java.net.URI
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.toolbox.core.parser.RellCommonTokenStream
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError
import net.postchain.rell.toolbox.linter.FormatterIssue
import net.postchain.rell.toolbox.linter.issues.LinterIssue

class SerializableResource(
    val parseTree: RellParser.RuleX_RootParserContext,
    val moduleInfo: IdeModuleInfo?,
    val fileUri: URI,
    val workspaceUri: URI,
    val ast: S_RellFile,
    val syntaxErrors: List<SyntaxError> = listOf(),
    val semanticErrors: List<C_Message> = listOf(),
    val symbolInfos: Map<S_Pos, SerializableSymbolInfo>,
    val checksum: String,
    val tokenStream: RellCommonTokenStream,
    val linterIssues: List<LinterIssue> = listOf(),
    val formatterIssues: List<FormatterIssue> = listOf()
)