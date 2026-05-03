/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.compiler

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.parser.antlr.RellAntlrVisitor
import net.postchain.rell.base.compiler.parser.antlr.RellManualParser
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.toImmList
import org.antlr.v4.runtime.BufferedTokenStream

object RellCompilerApi {

    /**
     * Build a Rell `S_RellFile` AST from a parse tree produced by the new `RellManualParser`.
     *
     * Phase 4 of the better-parse → ANTLR migration: this used to delegate to the auto-generated
     * `AntlrToRell` transformer (driven by the legacy `Rell.g4`); it now uses `RellAntlrVisitor`,
     * the hand-written visitor over the canonical `RellManual.g4` grammar that lives in
     * `:rell-base:frontend`.
     */
    fun antlrToRellAst(
        path: RellCompilerFilePath,
        antlrRootNode: RellManualParser.FileContext,
        tokenStream: BufferedTokenStream? = null,
    ): Pair<S_RellFile, List<C_Error>> = RellCompilerFilePathHolder.overrideCurrentFile(path) {
        val filePath = C_ParserFilePath(path.cPath, path.idePath)
        val visitor = RellAntlrVisitor(filePath, attachmentMode = true, tokenStream = tokenStream)
        val ast = try {
            visitor.toFile(antlrRootNode)
        } catch (e: C_Error) {
            // Visitor threw a compilation error; surface it as a message and return an empty file.
            return@overrideCurrentFile S_RellFile(null, emptyList<net.postchain.rell.base.compiler.ast.S_Definition>().toImmList()) to listOf(e)
        }
        ast to emptyList()
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
            checkNotNull(rModule) { "Invalid module name: [$s]" }
        }
        val res = IdeApi.compile(srcDir, rModules.toImmList(), options)
        return res.messages
    }
}
