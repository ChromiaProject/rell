/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.S_PosRange
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.core.C_NamespaceContext
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.doc.DocCodeTokenVisitor
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.immMultimapOf
import net.postchain.rell.base.utils.toImmMap

data class C_IdeCompletionsOptions(
    val file: C_SourcePath,
    val pos: Int?,
) {
    init {
        require(pos == null || pos >= 0)
    }

    fun toPojo(): Map<String, Any> {
        val res = mutableMapOf<String, Any>("file" to file.str())
        if (pos != null) res["pos"] = pos
        return res.toImmMap()
    }

    companion object {
        fun fromPojo(map: Map<String, Any>): C_IdeCompletionsOptions {
            val file = C_SourcePath.parse(map.getValue("file") as String)
            val pos = map["pos"]?.let { it as Int }
            return C_IdeCompletionsOptions(file, pos)
        }
    }
}

object C_IdeCompletionsUtils {
    fun getCompilerOptions(
        sourceDir: C_SourceDir,
        filePath: C_SourcePath,
        pos: Int?,
        baseOptions: C_CompilerOptions,
    ): C_CompilerOptions? {
        require(pos == null || pos >= 0) { pos!! }

        val sourceFile = sourceDir.file(filePath)
        sourceFile ?: return null

        sourceFile.readText()

        return baseOptions.toBuilder()
            .ideDocSymbolsEnabled(true)
            .ideCompletions(C_IdeCompletionsOptions(filePath, pos))
            .build()
    }

    fun getIdeCompletionLocation(defName: C_DefinitionName): String {
        val defPath = defName.parentPath()
        val defModule = defPath.module.str()
        return if (defPath.path.isEmpty()) defModule else "$defModule:${defPath.path.joinToString(".")}"
    }

    fun makeIdeCompletion(defName: C_DefinitionName, doc: DocSymbol, targetDoc: DocSymbol = doc): IdeCompletion {
        val docComp = targetDoc.declaration.completion
        val location = getIdeCompletionLocation(defName)
        return IdeCompletion(targetDoc.kind, doc.symbolName, docComp?.params ?: "", docComp?.result, location)
    }

    fun docCodeToStr(docCode: DocCode): String {
        val b = StringBuilder()

        docCode.visit(object: DocCodeTokenVisitor {
            override fun tab() {
                b.append(" ")
            }

            override fun raw(s: String) {
                b.append(s)
            }

            override fun keyword(s: String) {
                b.append(s)
            }

            override fun link(s: String) {
                b.append(s)
            }
        })

        return b.toString()
    }
}

sealed class C_IdeCompletionsContext {
    abstract fun trackScope(nsCtx: C_NamespaceContext, range: S_PosRange?)
}

class C_IdeCompletionsManager(compilerOptions: C_CompilerOptions) {
    private val options = compilerOptions.ideCompletions
    private val activeFile = options?.file

    val nopCtx: C_IdeCompletionsContext = C_IdeCompletionsContext_Nop

    private val activeCtx = C_IdeCompletionsContext_Active(options?.pos)

    fun getFileContext(path: C_SourcePath): C_IdeCompletionsContext {
        return if (path == activeFile) activeCtx else nopCtx
    }

    fun finish(): Multimap<String, IdeCompletion> {
        return activeCtx.finish()
    }
}

private data object C_IdeCompletionsContext_Nop: C_IdeCompletionsContext() {
    override fun trackScope(nsCtx: C_NamespaceContext, range: S_PosRange?) {
        // Do nothing.
    }
}

private class C_IdeCompletionsContext_Active(private val filePos: Int?): C_IdeCompletionsContext() {
    private var ideScopeNsContext: C_NamespaceContext? = null
    private var finished = false

    override fun trackScope(nsCtx: C_NamespaceContext, range: S_PosRange?) {
        check(!finished)

        // Assuming this function is always called for a more narrow range than the previous one.

        if (filePos == null && ideScopeNsContext != null && range != null) {
            // No specific position - taking the first context, which must be the topmost one.
            return
        }

        if (range != null && filePos != null
            && (filePos < range.start.offset() || filePos > range.end.offset())
        ) {
            // Target position is not in range.
            return
        }

        ideScopeNsContext = nsCtx
    }

    fun finish(): Multimap<String, IdeCompletion> {
        check(!finished)
        finished = true

        val nsCtx = ideScopeNsContext
        return nsCtx?.ideCompletions() ?: immMultimapOf()
    }
}
