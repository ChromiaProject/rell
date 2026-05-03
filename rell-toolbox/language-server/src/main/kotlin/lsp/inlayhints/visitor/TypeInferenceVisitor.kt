/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.inlayhints.visitor

import net.postchain.rell.base.compiler.parser.antlr.RellManualBaseVisitor
import net.postchain.rell.base.compiler.parser.antlr.RellManualParser
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.createTypeInlayHint
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.isInRange
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.Position

/**
 * Inlay-hint visitor for variable-declaration type inference.
 *
 * Migrated from the legacy `Rell.g4` grammar to the canonical `RellManual.g4`.
 * The new grammar collapses `varStmt`/`forStmt` into `varStmtAlt`/`forStmtAlt`
 * children of `statement`, and `attrHeader` exposes its `nameTypeAttrHeader`
 * vs. `anonAttrHeader` alts as sealed-style subclasses.
 */
class TypeInferenceVisitor(
    private val resource: Resource,
    private val range: org.eclipse.lsp4j.Range,
    private val hints: MutableList<InlayHint>
) : RellManualBaseVisitor<Unit>() {

    override fun visitVarStmtAlt(ctx: RellManualParser.VarStmtAltContext) {
        val declarator = ctx.varDeclarator() ?: return super.visitVarStmtAlt(ctx).let { }
        if (declarator is RellManualParser.SimpleVarDeclaratorContext) {
            processSimpleDeclarator(declarator) { extractTypeForVarStmt(ctx) }
        }
        super.visitVarStmtAlt(ctx)
    }

    override fun visitConstantDef(ctx: RellManualParser.ConstantDefContext) {
        // If an explicit type is present (`val x : T = ...`), the second child
        // of `:` will be a TypeContext — skip the hint.
        if (ctx.type() != null) return super.visitConstantDef(ctx).let { }

        val nameTerminal = ctx.RULE_ID() ?: return super.visitConstantDef(ctx).let { }
        val nameInterval = Interval(nameTerminal.symbol.startIndex, nameTerminal.symbol.stopIndex + 1)
        val defPosition = Position(nameTerminal.symbol.line - 1, nameTerminal.symbol.charPositionInLine + nameTerminal.text.length)

        if (!isInRange(defPosition, range)) return super.visitConstantDef(ctx).let { }

        val symbolInfo = resource.getSymbolInfoForInterval(nameInterval) ?: return super.visitConstantDef(ctx).let { }
        val type = extractTypeFromSymbolInfo(symbolInfo) ?: return super.visitConstantDef(ctx).let { }
        hints.add(createTypeInlayHint(defPosition, type))
        super.visitConstantDef(ctx)
    }

    override fun visitForStmtAlt(ctx: RellManualParser.ForStmtAltContext) {
        val declarator = ctx.varDeclarator()
        when (declarator) {
            is RellManualParser.SimpleVarDeclaratorContext -> processSimpleDeclarator(declarator) {
                extractTypeForAnonName(declarator)
            }
            is RellManualParser.TupleVarDeclaratorContext -> processTupleDeclarator(declarator)
            else -> {}
        }
        super.visitForStmtAlt(ctx)
    }

    private fun processTupleDeclarator(tuple: RellManualParser.TupleVarDeclaratorContext) {
        for (child in tuple.varDeclarator()) {
            when (child) {
                is RellManualParser.SimpleVarDeclaratorContext -> processSimpleDeclarator(child) {
                    extractTypeForAnonName(child)
                }
                is RellManualParser.TupleVarDeclaratorContext -> processTupleDeclarator(child)
            }
        }
    }

    private fun processSimpleDeclarator(
        decl: RellManualParser.SimpleVarDeclaratorContext,
        getType: () -> String?
    ) {
        val header = decl.attrHeader() ?: return
        // Skip when the user wrote an explicit type (`name: T`).
        if (header is RellManualParser.NameTypeAttrHeaderContext) return
        val anon = header as? RellManualParser.AnonAttrHeaderContext ?: return

        val nameNode: ParserRuleContext = anon.qualifiedName() ?: return
        val defPosition = Position(nameNode.start.line - 1, nameNode.stop.charPositionInLine + nameNode.text.length)
        if (!isInRange(defPosition, range)) return

        val type = getType() ?: return
        hints.add(createTypeInlayHint(defPosition, type))
    }

    private fun extractTypeForVarStmt(ctx: RellManualParser.VarStmtAltContext): String? {
        val decl = ctx.varDeclarator() as? RellManualParser.SimpleVarDeclaratorContext ?: return null
        val anon = decl.attrHeader() as? RellManualParser.AnonAttrHeaderContext ?: return null
        return extractTypeForAnonName(decl, anon)
    }

    private fun extractTypeForAnonName(decl: RellManualParser.SimpleVarDeclaratorContext): String? {
        val anon = decl.attrHeader() as? RellManualParser.AnonAttrHeaderContext ?: return null
        return extractTypeForAnonName(decl, anon)
    }

    private fun extractTypeForAnonName(
        @Suppress("UNUSED_PARAMETER") decl: RellManualParser.SimpleVarDeclaratorContext,
        anon: RellManualParser.AnonAttrHeaderContext
    ): String? {
        val nameNode = anon.qualifiedName() ?: return null
        val interval = Interval(nameNode.start.startIndex, nameNode.stop.stopIndex + 1)
        val symbolInfo = resource.getSymbolInfoForInterval(interval) ?: return null
        return extractTypeFromSymbolInfo(symbolInfo)
    }

    private fun extractTypeFromSymbolInfo(symbolInfo: IdeSymbolInfo): String? =
        symbolInfo.kind.takeIf { it in variableStatementKind }
            ?.let { symbolInfo.doc?.let { extractTypeFromDocSymbol(it) } }

    private fun extractTypeFromDocSymbol(docSymbol: DocSymbol): String? =
        docSymbol.declaration.completion?.result

    companion object {
        val variableStatementKind = listOf(
            IdeSymbolKind.LOC_VAL,
            IdeSymbolKind.LOC_VAR,
            IdeSymbolKind.DEF_CONSTANT
        )
    }
}
