package net.postchain.rell.toolbox.compiler

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.toolbox.parser.RellParser.RuleX_RootParserContext
import net.postchain.rell.toolbox.transformer.AntlrToRell
import net.postchain.rell.toolbox.transformer.AntlrToRellContext
import org.antlr.v4.runtime.TokenStream

object RellCompilerApi {

    fun antlrToRellAst(
        path: RellCompilerFilePath,
        antlrRootNode: RuleX_RootParserContext,
        tokenStream: TokenStream,
    ): Pair<S_RellFile, List<C_Error>> {
        return AntlrToRellContext.runWithContext(tokenStream) { ctx ->
            RellCompilerFilePathHolder.overrideCurrentFile(path) {
                val root = AntlrToRell.process(ctx, antlrRootNode)
                root as S_RellFile
            }
        }
    }

    fun validateSimple(srcDir: C_SourceDir, moduleName: String): String {
        val modules = listOf(moduleName)
        val options: C_CompilerOptions = C_CompilerOptions.builder()
            .gtv(false)
            .deprecatedError(false)
            .build()

        val messages = validate(srcDir, modules, options)
        val errors = messages.filter { m -> m.type == C_MessageType.ERROR }
        return if (errors.isEmpty()) {
            "OK"
        } else {
            val e: C_Message = errors[0]
            "ct_err:" + e.code
        }
    }

    private fun validate(srcDir: C_SourceDir, modules: List<String>, options: C_CompilerOptions): List<C_Message> {
        val rModules = modules.map { s ->
            val rModule = IdeApi.parseModuleName(s)
            check(rModule != null) { "Invalid module name: [$s]" }
            rModule
        }
        val res = IdeApi.compile(srcDir, rModules.toImmList(), options)
        return res.messages
    }
}
