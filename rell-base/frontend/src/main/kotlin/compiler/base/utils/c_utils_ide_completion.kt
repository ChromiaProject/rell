/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.compiler.ast.S_PosRange
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.doc.DocCodeTokenVisitor
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeCompletion

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
        val location = getIdeCompletionLocation(defName)
        return makeIdeCompletion0(doc, targetDoc, location)
    }

    fun makeIdeCompletion(doc: DocSymbol, location: String? = null): IdeCompletion {
        return makeIdeCompletion0(doc, doc, location)
    }

    private fun makeIdeCompletion0(doc: DocSymbol, targetDoc: DocSymbol, location: String?): IdeCompletion {
        val docComp = targetDoc.declaration.completion
        val deprecated = doc.declaration.isDeprecated
        return IdeCompletion(targetDoc.kind, doc.symbolName, docComp?.params, docComp?.result, location, doc, deprecated)
    }

    fun docCodeToStr(docCode: DocCode): String = buildString {
        docCode.visit(object: DocCodeTokenVisitor {
            override fun tab() {
                append(" ")
            }

            override fun raw(s: String) {
                append(s)
            }

            override fun keyword(s: String) {
                append(s)
            }

            override fun link(s: String) {
                append(s)
            }
        })
    }

    fun isTargetScope(compilerOptions: C_CompilerOptions, filePath: C_SourcePath, range: S_PosRange?): Boolean {
        val options = compilerOptions.ideCompletions
        val ideFilePos = options?.pos
        if (ideFilePos == null && range != null) {
            return false
        }

        if (options?.file != filePath) {
            return false
        }

        if (range != null && ideFilePos != null
            && (ideFilePos < range.start.offset() || ideFilePos > range.end.offset())
        ) {
            return false
        }

        return true
    }
}

interface C_IdeCompletionsScopeProvider {
    fun ideCompletionsScope(): C_IdeCompletionsScope
}

class C_IdeCompletionsScope(
    val parent: C_IdeCompletionsScope?,
    val getter: C_LateGetter<ImmMultimap<String, IdeCompletion>>,
)

class C_IdeCompletionsContext(
    private val filePath: C_SourcePath?,
    compilerOptions: C_CompilerOptions,
) {
    private val options = compilerOptions.ideCompletions

    private val scopes = mutableListOf<C_IdeCompletionsScope>()
    private var finished = false

    fun trackScope(
        range: S_PosRange?,
        parentScopeProvider: C_IdeCompletionsScopeProvider?,
        getter: C_LateGetter<ImmMultimap<String, IdeCompletion>> = C_LateGetter.const(immMultimapOf()),
    ) {
        check(!finished)

        val ideFilePos = options?.pos
        if (ideFilePos == null && range != null) {
            return
        }

        if (options?.file != filePath) {
            return
        }

        if (range != null && ideFilePos != null
            && (ideFilePos < range.start.offset() || ideFilePos > range.end.offset())
        ) {
            return
        }

        val parentScope = parentScopeProvider?.ideCompletionsScope()
        scopes.add(C_IdeCompletionsScope(parentScope, getter))
    }

    fun finish(): ImmMultimap<String, IdeCompletion> {
        check(!finished)
        finished = true

        val res = mutableMultimapOf<String, IdeCompletion>()

        for (curScope in scopes) {
            finishScope(curScope, res)
        }

        return res.mapValues { it.value.toSet().toImmList() }.toImmMultimap()
    }

    private fun finishScope(scope: C_IdeCompletionsScope, res: MutableMultimap<String, IdeCompletion>) {
        var curScope: C_IdeCompletionsScope? = scope
        while (curScope != null) {
            res.putAll(curScope.getter.get())
            curScope = curScope.parent
        }
    }
}
