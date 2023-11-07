package net.postchain.rell.toolbox.core.compiler

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.toolbox.core.parser.RellParser.RuleX_RootParserContext

object RellcAPI {

    fun antlrToRellAst(
        path: RellcFilePath,
        antlrRootNode: RuleX_RootParserContext
    ): Pair<S_RellFile, List<C_Error>> {
        setupLogging()
        return AntlrToRellContext.runWithContext { ctx ->
            RellcFilePathHolder.overrideCurrentFile(path) {
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

    fun validate(srcDir: C_SourceDir, modules: List<String>, options: C_CompilerOptions): List<C_Message> {
        val rModules = modules.map { s ->
            val rModule = IdeApi.parseModuleName(s)
            check(rModule != null) { "Invalid module name: [$s]" }
            rModule
        }
        val res = IdeApi.compile(srcDir, rModules, options)
        return res.messages
    }
}
