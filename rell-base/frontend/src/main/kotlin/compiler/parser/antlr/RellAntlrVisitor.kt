/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser.antlr

import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.parser.RellTokenizer
import net.postchain.rell.base.model.AtCardinality
import net.postchain.rell.base.model.KeyIndexKind
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Converts ANTLR `RellManualParser` parse trees into Rell's compiler `S_*` AST.
 *
 * This is the ANTLR counterpart of `S_Grammar` in `grammar.kt`. The semantics of every visit
 * method mirrors the corresponding `map { ... }` lambda in `grammar.kt`.
 *
 * When [attachmentMode] is `true`, every `S_Pos` returned by this visitor is an [AntlrPos]
 * that carries the deepest enclosing `ParserRuleContext`, and every `S_Node` constructed
 * during the visit gets an [AntlrRellNodeAttachment] for that same context (via the
 * thread-local attachment-provider plumbing in `S_Node`). This is required by the toolbox
 * IDE features (LSP / indexer / outline / references) that need to walk back to the parse
 * tree from positions and AST nodes.
 *
 * The compiler-side parser keeps the default `attachmentMode = false` to avoid the
 * per-context bookkeeping overhead.
 */
class RellAntlrVisitor(
    private val filePath: C_ParserFilePath,
    private val attachmentMode: Boolean = false,
    private val tokenStream: BufferedTokenStream? = null,
) {

    private val contextStack = ArrayDeque<ParserRuleContext>()
    private val attachmentProvider: AntlrAttachmentProvider? =
        if (attachmentMode) AntlrAttachmentProvider() else null

    /** Push [ctx] for the duration of [block]; pop on exit (try/finally so non-local returns are safe). */
    private inline fun <T> withCtx(ctx: ParserRuleContext, block: () -> T): T {
        if (!attachmentMode) return block()
        contextStack.addLast(ctx)
        attachmentProvider!!.node = ctx
        try {
            return block()
        } finally {
            contextStack.removeLast()
            attachmentProvider.node = contextStack.lastOrNull()
        }
    }

    /** Run [block] with attachment-provider plumbing wired up if [attachmentMode] is on. */
    private inline fun <T> withAttachmentScope(crossinline block: () -> T): T {
        if (!attachmentMode) return block()
        var result: Any? = null
        S_Node.runWithAttachmentProvider(attachmentProvider!!) {
            result = block()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    // ---------------------------------------------------------------------------------------------
    // Top-level

    fun toFile(ctx: RellManualParser.FileContext): S_RellFile = withAttachmentScope {
        withCtx(ctx) {
            val header = ctx.moduleHeader()?.let { toModuleHeader(it) }
            val defs = ctx.annotatedDef().map { toAnnotatedDef(it) }.toImmList()
            S_RellFile(header, defs)
        }
    }

    fun toReplCommand(ctx: RellManualParser.ReplCommandContext): S_ReplCommand = withAttachmentScope {
        withCtx(ctx) {
            val steps = ctx.replStep().map { toReplStep(it) }
            val expr = ctx.expression()?.let { toExpression(it) }
            S_ReplCommand(steps, expr)
        }
    }

    private fun toReplStep(ctx: RellManualParser.ReplStepContext): S_ReplStep = withCtx(ctx) {
        when (ctx) {
            is RellManualParser.DefReplStepContext -> {
                val mods = toModifiers(ctx.modifiers())
                val def = toReplDef(ctx.replDef(), mods, ctx)
                S_DefinitionReplStep(def)
            }
            is RellManualParser.StmtReplStepContext -> S_StatementReplStep(toStatement(ctx.statement()))
            is RellManualParser.ExprReplStepContext -> {
                val expr = toExpression(ctx.expression())
                val semiTok = ctx.children.last { it is TerminalNode && it.text == ";" } as TerminalNode
                S_StatementReplStep(S_ExprStatement(expr, semiTok.symbol.toPos()))
            }
            else -> error("unknown replStep alt: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toReplDef(
        ctx: RellManualParser.ReplDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        ctx.entityDef()?.let { return@withCtx toEntityDef(it, modifiers, outerCtx) }
        ctx.objectDef()?.let { return@withCtx toObjectDef(it, modifiers, outerCtx) }
        ctx.structDef()?.let { return@withCtx toStructDef(it, modifiers, outerCtx) }
        ctx.enumDef()?.let { return@withCtx toEnumDef(it, modifiers, outerCtx) }
        ctx.functionDef()?.let { return@withCtx toFunctionDef(it, modifiers, outerCtx) }
        ctx.namespaceDef()?.let { return@withCtx toNamespaceDef(it, modifiers, outerCtx) }
        ctx.importDef()?.let { return@withCtx toImportDef(it, modifiers, outerCtx) }
        ctx.opDef()?.let { return@withCtx toOpDef(it, modifiers, outerCtx) }
        ctx.queryDef()?.let { return@withCtx toQueryDef(it, modifiers, outerCtx) }
        ctx.includeDef()?.let { return@withCtx toIncludeDef(it, modifiers, outerCtx) }
        error("unknown replDef alt")
    }

    fun toModuleHeader(ctx: RellManualParser.ModuleHeaderContext): S_ModuleHeader = withCtx(ctx) {
        val mods = toModifiers(ctx.modifiers())
        val moduleKwTok = ctx.children.first { it is TerminalNode && it.text == "module" } as TerminalNode
        return S_ModuleHeader(mods, moduleKwTok.symbol.toPos(), docCommentFor(ctx))
    }

    // ---------------------------------------------------------------------------------------------
    // Modifiers / annotations

    private fun toModifiers(ctx: RellManualParser.ModifiersContext): S_Modifiers = withCtx(ctx) {
        val mods = ctx.modifier().map { toModifier(it) }
        return if (mods.isEmpty()) S_Modifiers() else S_Modifiers(mods.toImmList())
    }

    private fun toModifier(ctx: RellManualParser.ModifierContext): S_Modifier = withCtx(ctx) {
        val ann = ctx.annotation()
        if (ann != null) {
            return toAnnotation(ann)
        }
        // Keyword modifier: abstract / mutable / override
        val tok = ctx.children.first { it is TerminalNode } as TerminalNode
        val kind = when (tok.text) {
            "abstract" -> S_KeywordModifierKind.ABSTRACT
            "mutable" -> S_KeywordModifierKind.MUTABLE
            "override" -> S_KeywordModifierKind.OVERRIDE
            else -> error("unknown keyword modifier: ${tok.text}")
        }
        val cName = C_Name.make(tok.symbol.toPos(), tok.text)
        return S_KeywordModifier(cName, kind)
    }

    private fun toAnnotation(ctx: RellManualParser.AnnotationContext): S_Annotation = withCtx(ctx) {
        val nameTok = ctx.RULE_ID()
        val name = S_Name(nameTok.symbol.toPos(), Name.of(nameTok.text))
        val args = ctx.annotationArgs()?.annotationArg()?.map { toAnnotationArg(it) } ?: emptyList()
        return S_Annotation(name, args.toImmList())
    }

    private fun toAnnotationArg(ctx: RellManualParser.AnnotationArgContext): S_AnnotationArg = withCtx(ctx) {
        ctx.qualifiedName()?.let { return S_AnnotationArg_Name(toQualifiedName(it)) }
        // Otherwise it must be a literal token
        val lit = literalFromAnnotationArg(ctx)
        return S_AnnotationArg_Value(lit)
    }

    private fun literalFromAnnotationArg(ctx: RellManualParser.AnnotationArgContext): S_LiteralExpr = withCtx(ctx) {
        ctx.RULE_NUMBER()?.let { tk ->
            val pos = tk.symbol.toPos()
            return S_IntegerLiteralExpr(pos, RellTokenizer.decodeInteger(pos, tk.text))
        }
        ctx.RULE_BIG_INTEGER()?.let { tk ->
            val pos = tk.symbol.toPos()
            return S_CommonLiteralExpr(pos, RellTokenizer.decodeBigInteger(pos, tk.text))
        }
        ctx.RULE_DECIMAL()?.let { tk ->
            val pos = tk.symbol.toPos()
            return S_CommonLiteralExpr(pos, RellTokenizer.decodeDecimal(pos, tk.text))
        }
        ctx.RULE_STRING()?.let { tk ->
            val pos = tk.symbol.toPos()
            return S_StringLiteralExpr(pos, decodeStringTokenText(pos, tk.text))
        }
        ctx.RULE_BYTES()?.let { tk ->
            val pos = tk.symbol.toPos()
            return S_ByteArrayLiteralExpr(pos, RellTokenizer.decodeByteArray(pos, decodeBytesTokenText(tk.text)))
        }
        // false/true/null
        val tok = ctx.children.first { it is TerminalNode } as TerminalNode
        val pos = tok.symbol.toPos()
        return when (tok.text) {
            "true" -> S_BooleanLiteralExpr(pos, true)
            "false" -> S_BooleanLiteralExpr(pos, false)
            "null" -> S_NullLiteralExpr(pos)
            else -> error("unknown annotation arg literal: ${tok.text}")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Definitions

    fun toAnnotatedDef(ctx: RellManualParser.AnnotatedDefContext): S_Definition = withCtx(ctx) {
        val mods = toModifiers(ctx.modifiers())
        return toAnyDef(ctx.anyDef(), mods, ctx)
    }

    private fun toAnyDef(
        ctx: RellManualParser.AnyDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        ctx.entityDef()?.let { return toEntityDef(it, modifiers, outerCtx) }
        ctx.objectDef()?.let { return toObjectDef(it, modifiers, outerCtx) }
        ctx.structDef()?.let { return toStructDef(it, modifiers, outerCtx) }
        ctx.enumDef()?.let { return toEnumDef(it, modifiers, outerCtx) }
        ctx.functionDef()?.let { return toFunctionDef(it, modifiers, outerCtx) }
        ctx.namespaceDef()?.let { return toNamespaceDef(it, modifiers, outerCtx) }
        ctx.importDef()?.let { return toImportDef(it, modifiers, outerCtx) }
        ctx.opDef()?.let { return toOpDef(it, modifiers, outerCtx) }
        ctx.queryDef()?.let { return toQueryDef(it, modifiers, outerCtx) }
        ctx.includeDef()?.let { return toIncludeDef(it, modifiers, outerCtx) }
        ctx.constantDef()?.let { return toConstantDef(it, modifiers, outerCtx) }
        error("unknown anyDef")
    }

    private fun makeDefBase(kwToken: Token, modifiers: S_Modifiers, outerCtx: ParserRuleContext): S_DefinitionBase {
        return S_DefinitionBase(kwToken.toPos(), modifiers, docCommentFor(outerCtx))
    }

    private fun toEntityDef(
        ctx: RellManualParser.EntityDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val deprecated = kwTok.text == "class"
        val deprecatedKwPos = if (deprecated) kwTok.toPos() else null
        val name = idTokenToName(ctx.RULE_ID())
        val annotations = ctx.entityAnnotations()?.RULE_ID()?.map { tk ->
            S_Name(tk.symbol.toPos(), Name.of(tk.text))
        }?.toImmList() ?: immListOf()
        val body = toEntityBody(ctx.entityBody())
        val base = makeDefBase(kwTok, modifiers, outerCtx)
        return S_EntityDefinition(base, deprecatedKwPos, name, annotations, body)
    }

    private fun toEntityBody(ctx: RellManualParser.EntityBodyContext?): ImmList<S_RelClause>? {
        if (ctx == null) return null
        // body alts: ';' (no clauses) or '{' relClause* '}'
        // The semicolon-only form is `null` body in the original grammar.kt (entityBodyShort).
        val firstChild = ctx.children?.firstOrNull()
        if (firstChild is TerminalNode && firstChild.text == ";") {
            return null
        }
        return ctx.relClause().map { toRelClause(it) }.toImmList()
    }

    private fun toRelClause(ctx: RellManualParser.RelClauseContext): S_RelClause = withCtx(ctx) {
        ctx.attributeClause()?.let { return toAttributeClause(it) }
        ctx.keyIndexClause()?.let { return toKeyIndexClause(it) }
        error("unknown relClause")
    }

    private fun toAttributeClause(ctx: RellManualParser.AttributeClauseContext): S_AttributeClause = withCtx(ctx) {
        val attr = toAttributeDefinition(ctx.baseAttributeDefinition())
        return S_AttributeClause(attr, docCommentFor(ctx))
    }

    private fun toKeyIndexClause(ctx: RellManualParser.KeyIndexClauseContext): S_KeyIndexClause = withCtx(ctx) {
        val kwTok = ctx.start
        val kind = when (kwTok.text) {
            "key" -> KeyIndexKind.KEY
            "index" -> KeyIndexKind.INDEX
            else -> error("unknown key/index keyword: ${kwTok.text}")
        }
        val attrs = ctx.baseAttributeDefinition().map { toAttributeDefinition(it) }.toImmList()
        return S_KeyIndexClause(kwTok.toPos(), kind, attrs, docCommentForToken(kwTok))
    }

    private fun toAttributeDefinition(ctx: RellManualParser.BaseAttributeDefinitionContext): S_AttributeDefinition = withCtx(ctx) {
        val mods = toModifiers(ctx.modifiers())
        val header = toAttrHeader(ctx.attrHeader())
        val expr = ctx.expression()?.let { toExpression(it) }
        return S_AttributeDefinition(mods, header, expr)
    }

    private fun toAttrHeader(ctx: RellManualParser.AttrHeaderContext): S_AttrHeader = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.NameTypeAttrHeaderContext -> {
                val name = idTokenToName(ctx.RULE_ID())
                S_NamedAttrHeader(name, toType(ctx.type()))
            }
            is RellManualParser.AnonAttrHeaderContext -> {
                val qName = toQualifiedName(ctx.qualifiedName())
                val nullable = ctx.children.any { it is TerminalNode && it.text == "?" }
                S_AnonAttrHeader(qName, nullable)
            }
            else -> error("unknown attrHeader: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toObjectDef(
        ctx: RellManualParser.ObjectDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val name = idTokenToName(ctx.RULE_ID())
        val attrs = ctx.attributeClause().map { toAttributeClause(it) }.toImmList()
        return S_ObjectDefinition(makeDefBase(kwTok, modifiers, outerCtx), name, attrs)
    }

    private fun toStructDef(
        ctx: RellManualParser.StructDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val deprecated = kwTok.text == "record"
        val deprecatedKwPos = if (deprecated) kwTok.toPos() else null
        val name = idTokenToName(ctx.RULE_ID())
        val attrs = ctx.attributeClause().map { toAttributeClause(it) }.toImmList()
        return S_StructDefinition(makeDefBase(kwTok, modifiers, outerCtx), deprecatedKwPos, name, attrs)
    }

    private fun toEnumDef(
        ctx: RellManualParser.EnumDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val ids = ctx.RULE_ID()
        // First RULE_ID is the enum name; remaining are values.
        val name = idTokenToName(ids[0])
        val values = ids.drop(1).map { tk ->
            S_EnumValue(idTokenToName(tk), docCommentForToken(tk.symbol))
        }.toImmList()
        return S_EnumDefinition(makeDefBase(kwTok, modifiers, outerCtx), name, values)
    }

    private fun toFunctionDef(
        ctx: RellManualParser.FunctionDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val qName = ctx.qualifiedName()?.let { toQualifiedName(it) }
        val params = toFormalParameters(ctx.formalParameters())
        val retType = ctx.type()?.let { toType(it) }
        // Tolerate ANTLR error recovery: functionBody may be null when the parser inserted a
        // missing-rule placeholder for malformed input.
        val body = ctx.functionBody()?.let { toFunctionBody(it) }
        return S_FunctionDefinition(makeDefBase(kwTok, modifiers, outerCtx), qName, params, retType, body)
    }

    private fun toFormalParameters(ctx: RellManualParser.FormalParametersContext?): ImmList<S_FormalParameter> {
        if (ctx == null) return immListOf()
        return withCtx(ctx) {
            ctx.formalParameter().map { toFormalParameter(it) }.toImmList()
        }
    }

    private fun toFormalParameter(ctx: RellManualParser.FormalParameterContext): S_FormalParameter = withCtx(ctx) {
        val mods = toModifiers(ctx.modifiers())
        val attr = toAttrHeader(ctx.attrHeader())
        val expr = ctx.expression()?.let { toExpression(it) }
        return S_FormalParameter(mods, attr, expr, docCommentFor(ctx))
    }

    private fun toFunctionBody(ctx: RellManualParser.FunctionBodyContext): S_FunctionBody? = withCtx(ctx) {
        val firstChild = ctx.children.first()
        if (firstChild is TerminalNode) {
            return when (firstChild.text) {
                ";" -> null
                "=" -> {
                    val expr = toExpression(ctx.expression()!!)
                    val endTok = ctx.children.last { it is TerminalNode && it.text == ";" } as TerminalNode
                    S_FunctionBodyShort(S_PosRange(firstChild.symbol.toPos(), endTok.symbol.toPos()), expr)
                }
                else -> error("unknown functionBody first token: ${firstChild.text}")
            }
        }
        // blockStmt
        val stmt = toBlockStatement(ctx.blockStmt()!!)
        return S_FunctionBodyFull(stmt)
    }

    private fun toQueryBody(ctx: RellManualParser.QueryBodyContext): S_FunctionBody = withCtx(ctx) {
        val firstChild = ctx.children.first()
        if (firstChild is TerminalNode && firstChild.text == "=") {
            val expr = toExpression(ctx.expression()!!)
            val endTok = ctx.children.last { it is TerminalNode && it.text == ";" } as TerminalNode
            return S_FunctionBodyShort(S_PosRange(firstChild.symbol.toPos(), endTok.symbol.toPos()), expr)
        }
        val stmt = toBlockStatement(ctx.blockStmt()!!)
        return S_FunctionBodyFull(stmt)
    }

    private fun toNamespaceDef(
        ctx: RellManualParser.NamespaceDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val qName = ctx.qualifiedName()?.let { toQualifiedName(it) }
        val defs = ctx.annotatedDef().map { toAnnotatedDef(it) }.toImmList()
        // Body pos range = '{' .. '}'
        val lcurl = ctx.children.first { it is TerminalNode && it.text == "{" } as TerminalNode
        val rcurl = ctx.children.last { it is TerminalNode && it.text == "}" } as TerminalNode
        val bodyRange = S_PosRange(lcurl.symbol.toPos(), rcurl.symbol.toPos())
        return S_NamespaceDefinition(makeDefBase(kwTok, modifiers, outerCtx), bodyRange, qName, defs)
    }

    private fun toImportDef(
        ctx: RellManualParser.ImportDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        // alias appears as RULE_ID iff there's a colon after it; but the only RULE_ID directly
        // under importDef is the alias if present (qualifiedName is a sub-rule).
        val alias = ctx.RULE_ID()?.let { idTokenToName(it) }
        // Tolerate ANTLR error recovery: importModule may be null when the parser inserted a
        // missing-rule placeholder for malformed input.
        val moduleCtx = ctx.importModule()
        val module = if (moduleCtx != null) toImportModule(moduleCtx) else {
            val pos = ctx.start?.toPos() ?: errorPos()
            S_ImportModulePath(null, S_QualifiedName(immListOf(S_Name(pos, PLACEHOLDER_NAME))))
        }
        val target = ctx.importTarget()?.let { toImportTarget(it) } ?: S_DefaultImportTarget
        return S_ImportDefinition(makeDefBase(kwTok, modifiers, outerCtx), alias, module, target)
    }

    private fun toImportModule(ctx: RellManualParser.ImportModuleContext): S_ImportModulePath = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.AbsoluteImportModuleContext -> {
                S_ImportModulePath(null, toQualifiedName(ctx.qualifiedName()))
            }
            is RellManualParser.RelativeImportModuleContext -> {
                val dot = ctx.children.first { it is TerminalNode && it.text == "." } as TerminalNode
                val qName = ctx.qualifiedName()?.let { toQualifiedName(it) }
                S_ImportModulePath(S_RelativeImportModulePath(dot.symbol.toPos(), 0), qName)
            }
            is RellManualParser.UpImportModuleContext -> {
                val carets = ctx.children.filter { it is TerminalNode && it.text == "^" }
                val firstCaret = carets.first() as TerminalNode
                val qName = ctx.qualifiedName()?.let { toQualifiedName(it) }
                S_ImportModulePath(S_RelativeImportModulePath(firstCaret.symbol.toPos(), carets.size), qName)
            }
            else -> error("unknown importModule: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toImportTarget(ctx: RellManualParser.ImportTargetContext): S_ImportTarget = withCtx(ctx) {
        ctx.importTargetExact()?.let { exact ->
            val items = exact.importTargetExactItem().map { toImportTargetExactItem(it) }.toImmList()
            return S_ExactImportTarget(items)
        }
        // wildcard '*'
        return S_WildcardImportTarget
    }

    private fun toImportTargetExactItem(ctx: RellManualParser.ImportTargetExactItemContext): S_ExactImportTargetItem = withCtx(ctx) {
        // Generated `RULE_ID()` returns only the directly-owned alias token (qualifiedName has its own).
        val alias = ctx.RULE_ID()?.let { idTokenToName(it) }
        // Tolerate ANTLR error recovery: qualifiedName() may be null when the parser inserted a
        // missing-rule placeholder. Synthesize an empty qualified-name anchored at the item's start.
        val name = toQualifiedNameOrPlaceholder(ctx.qualifiedName(), ctx)
        val wildcard = ctx.children?.any { it is TerminalNode && it.text == "*" } ?: false
        return S_ExactImportTargetItem(alias, name, wildcard, docCommentFor(ctx))
    }

    private fun toOpDef(
        ctx: RellManualParser.OpDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val name = idTokenToName(ctx.RULE_ID())
        val params = toFormalParameters(ctx.formalParameters())
        val body = toBlockStatement(ctx.blockStmt())
        return S_OperationDefinition(makeDefBase(kwTok, modifiers, outerCtx), name, params, body)
    }

    private fun toQueryDef(
        ctx: RellManualParser.QueryDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val name = idTokenToName(ctx.RULE_ID())
        val params = toFormalParameters(ctx.formalParameters())
        val retType = ctx.type()?.let { toType(it) }
        val body = toQueryBody(ctx.queryBody())
        return S_QueryDefinition(makeDefBase(kwTok, modifiers, outerCtx), name, params, retType, body)
    }

    private fun toIncludeDef(
        ctx: RellManualParser.IncludeDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        return S_IncludeDefinition(makeDefBase(kwTok, modifiers, outerCtx))
    }

    private fun toConstantDef(
        ctx: RellManualParser.ConstantDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val name = idTokenToName(ctx.RULE_ID())
        val type = ctx.type()?.let { toType(it) }
        val expr = toExpression(ctx.expression())
        return S_GlobalConstantDefinition(makeDefBase(kwTok, modifiers, outerCtx), name, type, expr)
    }

    // ---------------------------------------------------------------------------------------------
    // Types

    fun toType(ctx: RellManualParser.TypeContext): S_Type = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.FunctionTypeContext -> {
                val types = ctx.type()
                val params = types.dropLast(1).map { toType(it) }.toImmList()
                val result = toType(types.last())
                val startPos = ctx.start.toPos()
                S_FunctionType(startPos, params, result)
            }
            is RellManualParser.BasicTypeAltContext -> {
                var res = toPrimaryType(ctx.primaryType())
                for (child in ctx.children) {
                    if (child is TerminalNode && child.text == "?") {
                        res = S_NullableType(child.symbol.toPos(), res)
                    }
                }
                res
            }
            else -> error("unknown type: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toPrimaryType(ctx: RellManualParser.PrimaryTypeContext): S_Type = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.GenericOrNameTypeContext -> {
                val qName = toQualifiedName(ctx.qualifiedName())
                val typeArgs = ctx.type()
                if (typeArgs.isEmpty()) {
                    S_NameType(qName)
                } else {
                    S_GenericType(qName, typeArgs.map { toType(it) }.toImmList())
                }
            }
            is RellManualParser.TupleTypeContext -> {
                // Children form: '(' (RULE_ID ':')? type (',' (RULE_ID ':')? type)* ','? ')'
                // We need each field: optional name + type, in declaration order.
                val fields = mutableListOf<S_GenericTupleAttr<S_Type>>()
                val children = ctx.children
                var trailingComma = false
                var idx = 1 // skip '('
                while (idx < children.size) {
                    val cur = children[idx]
                    if (cur is TerminalNode) {
                        when (cur.text) {
                            ")" -> break
                            "," -> {
                                if (idx + 1 < children.size && children[idx + 1] is TerminalNode
                                    && (children[idx + 1] as TerminalNode).text == ")") {
                                    trailingComma = true
                                }
                                idx++
                            }
                            else -> idx++
                        }
                        continue
                    }
                    // cur is a TypeContext — but maybe preceded by RULE_ID ':'. Check at children[idx-2..idx-1].
                    var fieldName: S_Name? = null
                    if (idx >= 2) {
                        val tn1 = children[idx - 2]
                        val tn0 = children[idx - 1]
                        if (tn1 is TerminalNode && tn0 is TerminalNode && tn0.text == ":"
                            && tn1.symbol.type == RellManualParser.RULE_ID) {
                            fieldName = idTokenToName(tn1)
                        }
                    }
                    // Match grammar.kt: doc-comment attaches only when the field has a name.
                    val fieldComment = if (fieldName != null) {
                        docCommentForToken((children[idx - 2] as TerminalNode).symbol)
                    } else null
                    fields.add(S_GenericTupleAttr(fieldName, toType(cur as RellManualParser.TypeContext), fieldComment))
                    idx++
                }
                val singleField = getTupleSingleField(fields, trailingComma)
                singleField ?: S_TupleType(ctx.start.toPos(), fields.toImmList())
            }
            is RellManualParser.VirtualTypeContext -> {
                val kwTok = ctx.start
                S_VirtualType(kwTok.toPos(), toType(ctx.type()))
            }
            is RellManualParser.MirrorStructTypeContext -> {
                val kwTok = ctx.start
                val mutable = ctx.children.any { it is TerminalNode && it.text == "mutable" }
                S_MirrorStructType(kwTok.toPos(), mutable, toType(ctx.type()))
            }
            else -> error("unknown primaryType: ${ctx.javaClass.simpleName}")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Statements

    fun toStatement(ctx: RellManualParser.StatementContext): S_Statement = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.EmptyStmtContext -> S_EmptyStatement(ctx.start.toPos())
            is RellManualParser.VarStmtAltContext -> toVarStatement(ctx)
            is RellManualParser.ReturnStmtAltContext -> {
                val kwTok = ctx.start
                val expr = ctx.expression()?.let { toExpression(it) }
                val end = ctx.stop
                S_ReturnStatement(kwTok.toPos(), end.toPos(), expr)
            }
            is RellManualParser.BlockStmtAltContext -> toBlockStatement(ctx.blockStmt())
            is RellManualParser.IfStmtAltContext -> {
                val kwTok = ctx.start
                val cond = toExpression(ctx.expression())
                val stmts = ctx.statement()
                val trueStmt = toStatement(stmts[0])
                val falseStmt = if (stmts.size > 1) toStatement(stmts[1]) else null
                S_IfStatement(kwTok.toPos(), cond, trueStmt, falseStmt)
            }
            is RellManualParser.WhenStmtAltContext -> {
                val kwTok = ctx.start
                val expr = ctx.expression()?.let { toExpression(it) }
                val conds = ctx.whenCondition().map { toWhenCondition(it) }
                val stmts = ctx.statement().map { toStatement(it) }
                val cases = conds.zip(stmts) { c, s -> S_WhenStatementCase(c, s) }.toImmList()
                val end = ctx.stop
                S_WhenStatement(kwTok.toPos(), end.toPos(), expr, cases)
            }
            is RellManualParser.WhileStmtAltContext -> {
                val kwTok = ctx.start
                val expr = toExpression(ctx.expression())
                val stmt = toStatement(ctx.statement())
                S_WhileStatement(kwTok.toPos(), expr, stmt)
            }
            is RellManualParser.ForStmtAltContext -> {
                val kwTok = ctx.start
                val decl = toVarDeclarator(ctx.varDeclarator())
                val expr = toExpression(ctx.expression())
                val stmt = toStatement(ctx.statement())
                // headerEndPos is the ')' position (close of the for-header).
                val rpar = ctx.children.first { it is TerminalNode && it.text == ")" } as TerminalNode
                S_ForStatement(kwTok.toPos(), decl, expr, stmt, rpar.symbol.toPos())
            }
            is RellManualParser.BreakStmtAltContext -> {
                val start = ctx.start
                val end = ctx.stop
                S_BreakStatement(start.toPos(), end.toPos())
            }
            is RellManualParser.ContinueStmtAltContext -> {
                val start = ctx.start
                val end = ctx.stop
                S_ContinueStatement(start.toPos(), end.toPos())
            }
            is RellManualParser.UpdateStmtAltContext -> toUpdateStatement(ctx)
            is RellManualParser.DeleteStmtAltContext -> {
                val kwTok = ctx.start
                val target = toUpdateTarget(ctx.updateTarget())
                val end = ctx.stop
                S_DeleteStatement(kwTok.toPos(), end.toPos(), target)
            }
            is RellManualParser.GuardStmtAltContext -> {
                val kwTok = ctx.start
                val block = toBlockStatement(ctx.blockStmt())
                S_GuardStatement(kwTok.toPos(), block)
            }
            is RellManualParser.IncrementStmtAltContext -> {
                val opTok = ctx.start
                val baseExpr = toBaseExpr(ctx.baseExpr())
                val end = ctx.stop
                val opPos = opTok.toPos()
                val inc = opTok.text == "++"
                val sOp = S_PosValue<S_UnaryOp>(opPos, S_UnaryOp_IncDec(inc, false))
                val sExpr = S_UnaryExpr(opPos, sOp, baseExpr)
                S_ExprStatement(sExpr, end.toPos())
            }
            is RellManualParser.ExprStmtAltContext -> toExprStmt(ctx)
            else -> error("unknown statement: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toBlockStatement(ctx: RellManualParser.BlockStmtContext): S_BlockStatement = withCtx(ctx) {
        val lcurl = ctx.start
        val rcurl = ctx.stop
        val stmts = ctx.statement().map { toStatement(it) }.toImmList()
        return S_BlockStatement(S_PosRange(lcurl.toPos(), rcurl.toPos()), stmts)
    }

    private fun toVarStatement(ctx: RellManualParser.VarStmtAltContext): S_VarStatement = withCtx(ctx) {
        val kwTok = ctx.start
        val mutable = kwTok.text == "var"
        val decl = toVarDeclarator(ctx.varDeclarator())
        val expr = ctx.expression()?.let { toExpression(it) }
        val end = ctx.stop
        return S_VarStatement(kwTok.toPos(), end.toPos(), decl, expr, mutable, docCommentForToken(kwTok))
    }

    private fun toVarDeclarator(ctx: RellManualParser.VarDeclaratorContext): S_VarDeclarator = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.SimpleVarDeclaratorContext -> S_SimpleVarDeclarator(toAttrHeader(ctx.attrHeader()))
            is RellManualParser.TupleVarDeclaratorContext -> {
                val pos = ctx.start.toPos()
                val subs = ctx.varDeclarator().map { toVarDeclarator(it) }.toImmList()
                S_TupleVarDeclarator(pos, subs)
            }
            else -> error("unknown varDeclarator: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toWhenCondition(ctx: RellManualParser.WhenConditionContext): S_WhenCondition = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.WhenConditionElseContext -> S_WhenConditionElse(ctx.start.toPos())
            is RellManualParser.WhenConditionExprContext -> {
                val exprs = ctx.expression().map { toExpression(it) }.toImmList()
                S_WhenConditionExpr(exprs)
            }
            else -> error("unknown whenCondition: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toExprStmt(ctx: RellManualParser.ExprStmtAltContext): S_Statement = withCtx(ctx) {
        val baseExpr = toBaseExpr(ctx.baseExpr())
        val rhs = ctx.expression()
        val end = ctx.stop
        if (rhs == null) {
            return S_ExprStatement(baseExpr, end.toPos())
        }
        // Find the assignment operator token between baseExpr and expression.
        val opTok = ctx.children.first { ch ->
            ch is TerminalNode && ch.text in ASSIGN_OP_TEXTS
        } as TerminalNode
        val opCode = assignOpCode(opTok.text)
        val srcExpr = toExpression(rhs)
        return S_AssignStatement(baseExpr, S_PosValue(opTok.symbol.toPos(), opCode), srcExpr, end.toPos())
    }

    private fun toUpdateStatement(ctx: RellManualParser.UpdateStmtAltContext): S_UpdateStatement = withCtx(ctx) {
        val kwTok = ctx.start
        val target = toUpdateTarget(ctx.updateTarget())
        // updateWhat: list of `(('.'? RULE_ID ('=' | '+=' | ...))? expression)`
        // We need to walk children of the update statement: after `'(' ... ')'` the inner is what.
        val whatItems = parseUpdateWhat(ctx)
        val end = ctx.stop
        return S_UpdateStatement(kwTok.toPos(), end.toPos(), target, whatItems.toImmList())
    }

    private fun parseUpdateWhat(ctx: RellManualParser.UpdateStmtAltContext): List<S_UpdateWhat> = withCtx(ctx) {
        // Walk children, find the open '(' that starts the update-what list (the one AFTER the
        // updateTarget context). The structure is:
        //   'update' updateTarget '(' (whatItem (',' whatItem)* ','?) ')' ';'
        val children = ctx.children
        // Find last '(' before any ')' close at top level — the one after updateTarget.
        // Easier: find index of updateTarget context, then the next child is '('.
        val updateTargetIdx = children.indexOfFirst { it is RellManualParser.UpdateTargetContext }
        require(updateTargetIdx >= 0)
        var i = updateTargetIdx + 1
        // Skip until '('
        while (i < children.size && !(children[i] is TerminalNode && (children[i] as TerminalNode).text == "(")) i++
        require(i < children.size)
        i++ // skip '('
        val items = mutableListOf<S_UpdateWhat>()
        while (i < children.size) {
            val cur = children[i]
            if (cur is TerminalNode) {
                when (cur.text) {
                    ")" -> break
                    "," -> { i++; continue }
                    else -> { i++; continue }
                }
            }
            // cur is an ExpressionContext.
            // Look back for `('.'? RULE_ID assignOp)?`.
            var nameTok: TerminalNode? = null
            var opTok: TerminalNode? = null
            // Walk backward from i-1 to either ',' or '(' to find the prefix tokens.
            var j = i - 1
            val prefixTokens = mutableListOf<TerminalNode>()
            while (j > updateTargetIdx) {
                val pc = children[j]
                if (pc is TerminalNode && (pc.text == "," || pc.text == "(")) break
                if (pc is TerminalNode) prefixTokens.add(0, pc)
                else break
                j--
            }
            // Patterns:
            //  []                     -> no name
            //  [ID, op]               -> name=ID
            //  [., ID, op]            -> name=ID (dot prefix ignored for AST)
            if (prefixTokens.size >= 2) {
                val maybeOp = prefixTokens.last()
                val maybeId = prefixTokens[prefixTokens.size - 2]
                if (maybeOp.text in ASSIGN_OP_TEXTS && maybeId.symbol.type == RellManualParser.RULE_ID) {
                    nameTok = maybeId
                    opTok = maybeOp
                }
            }
            val expr = toExpression(cur as RellManualParser.ExpressionContext)
            val item = if (nameTok == null) {
                S_UpdateWhat(expr.startPos, null, null, expr)
            } else {
                val sName = idTokenToName(nameTok)
                S_UpdateWhat(sName.pos, sName, assignOpCode(opTok!!.text), expr)
            }
            items.add(item)
            i++
        }
        return items
    }

    private fun toUpdateTarget(ctx: RellManualParser.UpdateTargetContext): S_UpdateTarget = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.UpdateTargetAtContext -> {
                val cardinality = toAtCardinality(ctx.atExprAt())
                val where = toAtExprWhere(ctx.atExprWhere())
                val from = parseUpdateFromList(ctx)
                S_UpdateTarget_Simple(cardinality.value, from, where)
            }
            is RellManualParser.UpdateTargetExprContext -> {
                val head = toBaseExprHead(ctx.baseExprHead())
                val tails = ctx.baseExprTailNoCallNoAt().map { toTailNoCallNoAt(it) }
                val expr = applyTails(head, tails)
                S_UpdateTarget_Expr(expr)
            }
            else -> error("unknown updateTarget: ${ctx.javaClass.simpleName}")
        }
    }

    private fun parseUpdateFromList(ctx: RellManualParser.UpdateTargetAtContext): ImmList<S_UpdateFromItem> = withCtx(ctx) {
        val qNames = ctx.qualifiedName()
        // Two shapes:
        //  - single qualifiedName (no parens)        -> single item with no alias
        //  - '(' ((RULE_ID ':')? qualifiedName)+ ')' -> multi items, each may have alias
        val firstChild = ctx.children.first()
        if (firstChild is RellManualParser.QualifiedNameContext) {
            // Single name without parens
            val qn = toQualifiedName(qNames[0])
            return immListOf(S_UpdateFromItem(null, qn, null))
        }
        // Multi: walk children, picking each `(RULE_ID ':')?` alias before each qualifiedName,
        // until we hit the atExprAt child.
        val items = mutableListOf<S_UpdateFromItem>()
        for (qnCtx in qNames) {
            // Find this qnCtx's index in children, look back at preceding terminals for alias.
            val idxInChildren = ctx.children.indexOf(qnCtx)
            var alias: S_Name? = null
            var aliasTok: Token? = null
            if (idxInChildren >= 2) {
                val tn1 = ctx.children[idxInChildren - 2]
                val tn0 = ctx.children[idxInChildren - 1]
                if (tn1 is TerminalNode && tn0 is TerminalNode && tn0.text == ":"
                    && tn1.symbol.type == RellManualParser.RULE_ID) {
                    alias = idTokenToName(tn1)
                    aliasTok = tn1.symbol
                }
            }
            // Match grammar.kt: comment only when alias is present.
            val itemComment = aliasTok?.let { docCommentForToken(it) }
            items.add(S_UpdateFromItem(alias, toQualifiedName(qnCtx), itemComment))
        }
        return items.toImmList()
    }

    // ---------------------------------------------------------------------------------------------
    // Expressions

    fun toExpression(ctx: RellManualParser.ExpressionContext): S_Expr = withCtx(ctx) {
        // Children are: prefix-op* operand (binary-op prefix-op* operand)*
        // - operand = ifExpr | whenExpr | baseExpr
        // - prefix-ops are inline TerminalNodes ('+'/'-'/'not'/'++'/'--')
        // - binary-op is one or two TerminalNodes ('not' 'in' is two)
        // Filter out ANTLR ErrorNodes inserted during error recovery so the visitor can build
        // a partial AST instead of crashing on synthetic placeholders.
        val children = ctx.children.filter { it !is ErrorNode }
        val operands = mutableListOf<S_Expr>()
        val operators = mutableListOf<S_PosValue<S_BinaryOp>>()

        var i = 0
        while (i < children.size) {
            // Collect prefix operators.
            val prefix = mutableListOf<TerminalNode>()
            while (i < children.size) {
                val ch = children[i]
                if (ch is TerminalNode && ch.text in PREFIX_OP_TEXTS) {
                    prefix.add(ch)
                    i++
                } else break
            }
            // Read operand: ifExpr | whenExpr | baseExpr (a sub-context).
            // Under error recovery the operand may be missing entirely or may be a stray
            // TerminalNode; synthesize a placeholder S_NameExpr in those cases.
            if (i >= children.size) {
                val pos = ctx.start?.toPos() ?: errorPos()
                operands.add(S_NameExpr(S_QualifiedName(immListOf(S_Name(pos, PLACEHOLDER_NAME)))))
                break
            }
            val opCtx = children[i]
            i++
            var opExpr: S_Expr = when (opCtx) {
                is RellManualParser.IfExprContext -> toIfExpr(opCtx)
                is RellManualParser.WhenExprContext -> toWhenExpr(opCtx)
                is RellManualParser.BaseExprContext -> toBaseExpr(opCtx)
                else -> {
                    // ErrorNode / unexpected TerminalNode: synthesize a placeholder name expr at
                    // the offending position so the surrounding expression remains well-formed.
                    val pos = (opCtx as? TerminalNode)?.symbol?.toPos()
                        ?: (opCtx as? ParserRuleContext)?.start?.toPos()
                        ?: ctx.start?.toPos()
                        ?: errorPos()
                    S_NameExpr(S_QualifiedName(immListOf(S_Name(pos, PLACEHOLDER_NAME))))
                }
            }
            // Apply prefix operators (right-to-left): outermost first.
            for (p in prefix.reversed()) {
                val pos = p.symbol.toPos()
                val unaryOp: S_UnaryOp = when (p.text) {
                    "+" -> S_UnaryOp_Plus
                    "-" -> S_UnaryOp_Minus
                    "not" -> S_UnaryOp_Not
                    "++" -> S_UnaryOp_IncDec(inc = true, post = false)
                    "--" -> S_UnaryOp_IncDec(inc = false, post = false)
                    else -> error("unknown prefix op: ${p.text}")
                }
                opExpr = S_UnaryExpr(pos, S_PosValue(pos, unaryOp), opExpr)
            }
            operands.add(opExpr)

            // Read possible binary operator and continue.
            if (i < children.size) {
                val ch = children[i]
                require(ch is TerminalNode) { "Expected binary op terminal" }
                val opPos = ch.symbol.toPos()
                if (ch.text == "not") {
                    // Must be followed by 'in'
                    val tn2 = children[i + 1] as TerminalNode
                    require(tn2.text == "in")
                    operators.add(S_PosValue(opPos, S_BinaryOp.NOT_IN))
                    i += 2
                } else {
                    val sym = ch.text
                    val op = BIN_OP_BY_SYMBOL[sym] ?: error("unknown binary op: $sym")
                    operators.add(S_PosValue(opPos, op))
                    i += 1
                }
            }
        }

        require(operands.size == operators.size + 1)
        if (operators.isEmpty()) return operands[0]

        // Apply operator precedence to fold into S_BinaryExpr trees, matching the better-parse
        // grammar's left-associative head + tail structure.
        return foldBinary(operands, operators)
    }

    private fun foldBinary(operands: List<S_Expr>, operators: List<S_PosValue<S_BinaryOp>>): S_Expr {
        // The original grammar.kt's `binaryExpr` produces `S_BinaryExpr(head, tail)` with the
        // tail being a flat list — precedence resolution happens later in S_BinaryExpr.compile.
        // We replicate exactly that: a single S_BinaryExpr with a flat tail.
        val tail = operators.zip(operands.drop(1)) { op, expr -> S_BinaryExprTail(op, expr) }.toImmList()
        return S_BinaryExpr(operands[0], tail)
    }

    private fun toIfExpr(ctx: RellManualParser.IfExprContext): S_Expr = withCtx(ctx) {
        val kwTok = ctx.start
        val exprs = ctx.expression()
        return S_IfExpr(kwTok.toPos(), toExpression(exprs[0]), toExpression(exprs[1]), toExpression(exprs[2]))
    }

    private fun toWhenExpr(ctx: RellManualParser.WhenExprContext): S_Expr = withCtx(ctx) {
        val kwTok = ctx.start
        val conds = ctx.whenCondition().map { toWhenCondition(it) }
        // The first ExpressionContext might be the optional `(expression)` subject; otherwise
        // expressions are interleaved with conditions: cond -> expr ; cond -> expr ; ...
        // grammar.kt: when '(' expression? ')' '{' (cond '->' expr) (';' cond '->' expr)* ';'? '}'
        val allExprs = ctx.expression()
        // If '(' is present (children[1] = '(' if subject), the first expression is the subject.
        val hasSubject = ctx.children.let { ch ->
            ch.size > 1 && ch[1] is TerminalNode && (ch[1] as TerminalNode).text == "("
        }
        val subject: S_Expr?
        val caseExprs: List<S_Expr>
        if (hasSubject) {
            subject = toExpression(allExprs[0])
            caseExprs = allExprs.drop(1).map { toExpression(it) }
        } else {
            subject = null
            caseExprs = allExprs.map { toExpression(it) }
        }
        require(conds.size == caseExprs.size)
        val cases = conds.zip(caseExprs) { c, e -> S_WhenExprCase(c, e) }.toImmList()
        return S_WhenExpr(kwTok.toPos(), subject, cases)
    }

    private fun toBaseExpr(ctx: RellManualParser.BaseExprContext): S_Expr = withCtx(ctx) {
        var expr: S_Expr = toBaseExprHead(ctx.baseExprHead())
        // Walk children of baseExpr after baseExprHead and apply tails in source order.
        var seenHead = false
        var i = 0
        while (i < ctx.children.size) {
            val ch = ctx.children[i]
            if (!seenHead) {
                if (ch is RellManualParser.BaseExprHeadContext) seenHead = true
                i++
                continue
            }
            when (ch) {
                is RellManualParser.BaseExprTailNoCallNoAtContext -> {
                    expr = applyTail(expr, toTailNoCallNoAt(ch))
                    i++
                }
                is RellManualParser.CallArgsContext -> {
                    val args = toCallArgs(ch)
                    expr = S_CallExpr(expr, args)
                    i++
                }
                is RellManualParser.AtExprAtContext -> {
                    // Sequence: atExprAt atExprWhere atExprWhat? atExprModifiers?
                    val cardinality = toAtCardinality(ch)
                    i++
                    val whereCtx = ctx.children[i] as RellManualParser.AtExprWhereContext
                    val where = toAtExprWhere(whereCtx)
                    i++
                    var what: S_AtExprWhat = S_AtExprWhat_Default()
                    var limit: S_Expr? = null
                    var offset: S_Expr? = null
                    if (i < ctx.children.size && ctx.children[i] is RellManualParser.AtExprWhatContext) {
                        what = toAtExprWhat(ctx.children[i] as RellManualParser.AtExprWhatContext)
                        i++
                    }
                    if (i < ctx.children.size && ctx.children[i] is RellManualParser.AtExprModifiersContext) {
                        val mods = toAtExprModifiers(ctx.children[i] as RellManualParser.AtExprModifiersContext)
                        limit = mods.first
                        offset = mods.second
                        i++
                    }
                    expr = S_AtExpr(
                        S_AtExprFrom_Simple(expr),
                        cardinality,
                        where,
                        what,
                        limit,
                        offset,
                    )
                }
                else -> i++
            }
        }
        return expr
    }

    private fun toBaseExprHead(ctx: RellManualParser.BaseExprHeadContext): S_Expr = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.AtExprContext -> {
                val fromItems = parseAtExprFromItems(ctx)
                val cardinality = toAtCardinality(ctx.atExprAt())
                val where = toAtExprWhere(ctx.atExprWhere())
                val what = ctx.atExprWhat()?.let { toAtExprWhat(it) } ?: S_AtExprWhat_Default()
                val mods = ctx.atExprModifiers()?.let { toAtExprModifiers(it) }
                val from = S_AtExprFrom_Complex(ctx.start.toPos(), fromItems.toImmList())
                S_AtExpr(from, cardinality, where, what, mods?.first, mods?.second)
            }
            is RellManualParser.NameExprContext -> S_NameExpr(toQualifiedName(ctx.qualifiedName()))
            is RellManualParser.DollarExprContext -> S_DollarExpr(ctx.start.toPos())
            is RellManualParser.AttrExprContext -> S_AttrExpr(ctx.start.toPos(), idTokenToName(ctx.RULE_ID()))
            is RellManualParser.IntExprContext -> {
                val tk = ctx.RULE_NUMBER()
                val pos = tk.symbol.toPos()
                S_IntegerLiteralExpr(pos, RellTokenizer.decodeInteger(pos, tk.text))
            }
            is RellManualParser.BigIntExprContext -> {
                val tk = ctx.RULE_BIG_INTEGER()
                val pos = tk.symbol.toPos()
                S_CommonLiteralExpr(pos, RellTokenizer.decodeBigInteger(pos, tk.text))
            }
            is RellManualParser.DecimalExprContext -> {
                val tk = ctx.RULE_DECIMAL()
                val pos = tk.symbol.toPos()
                S_CommonLiteralExpr(pos, RellTokenizer.decodeDecimal(pos, tk.text))
            }
            is RellManualParser.StringExprContext -> {
                val tk = ctx.RULE_STRING()
                val pos = tk.symbol.toPos()
                S_StringLiteralExpr(pos, decodeStringTokenText(pos, tk.text))
            }
            is RellManualParser.BytesExprContext -> {
                val tk = ctx.children.first { it is TerminalNode } as TerminalNode
                val pos = tk.symbol.toPos()
                S_ByteArrayLiteralExpr(pos, RellTokenizer.decodeByteArray(pos, decodeBytesTokenText(tk.text)))
            }
            is RellManualParser.TrueExprContext -> S_BooleanLiteralExpr(ctx.start.toPos(), true)
            is RellManualParser.FalseExprContext -> S_BooleanLiteralExpr(ctx.start.toPos(), false)
            is RellManualParser.NullExprContext -> S_NullLiteralExpr(ctx.start.toPos())
            is RellManualParser.TupleHeadContext -> toTupleHead(ctx)
            is RellManualParser.CreateExprContext -> toCreateExpr(ctx)
            is RellManualParser.MirrorStructExprContext -> {
                val mutable = ctx.children.any { it is TerminalNode && it.text == "mutable" }
                S_MirrorStructExpr(ctx.start.toPos(), mutable, toType(ctx.type()))
            }
            is RellManualParser.VirtualTypeExprContext -> {
                val virtType = S_VirtualType(ctx.start.toPos(), toType(ctx.type()))
                S_SpecialTypeExpr(virtType)
            }
            is RellManualParser.GenericTypeExprContext -> toGenericTypeExpr(ctx)
            is RellManualParser.EmptyMapLiteralExprContext -> {
                S_MapLiteralExpr(ctx.start.toPos(), immListOf())
            }
            is RellManualParser.NonEmptyMapLiteralExprContext -> {
                val exprs = ctx.expression().map { toExpression(it) }
                require(exprs.size % 2 == 0)
                val entries = (exprs.indices step 2).map { idx -> Pair(exprs[idx], exprs[idx + 1]) }.toImmList()
                S_MapLiteralExpr(ctx.start.toPos(), entries)
            }
            is RellManualParser.ListLiteralExprContext -> {
                val exprs = ctx.expression().map { toExpression(it) }.toImmList()
                S_ListLiteralExpr(ctx.start.toPos(), exprs)
            }
            else -> error("unknown baseExprHead: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toTupleHead(ctx: RellManualParser.TupleHeadContext): S_Expr = withCtx(ctx) {
        // Walk children, gathering optional `RULE_ID '='` followed by expression.
        val fields = mutableListOf<S_GenericTupleAttr<S_Expr>>()
        val children = ctx.children
        var i = 1 // skip '('
        var trailingComma = false
        while (i < children.size) {
            val ch = children[i]
            if (ch is TerminalNode) {
                when (ch.text) {
                    ")" -> break
                    "," -> {
                        if (i + 1 < children.size && children[i + 1] is TerminalNode
                            && (children[i + 1] as TerminalNode).text == ")") {
                            trailingComma = true
                        }
                        i++
                    }
                    else -> i++
                }
                continue
            }
            // Expression context — preceded by optional (RULE_ID '=').
            var fieldName: S_Name? = null
            if (i >= 2) {
                val tn1 = children[i - 2]
                val tn0 = children[i - 1]
                if (tn1 is TerminalNode && tn0 is TerminalNode && tn0.text == "="
                    && tn1.symbol.type == RellManualParser.RULE_ID) {
                    fieldName = idTokenToName(tn1)
                }
            }
            // Match grammar.kt: doc-comment attaches only when the field has a name.
            val fieldComment = if (fieldName != null) {
                docCommentForToken((children[i - 2] as TerminalNode).symbol)
            } else null
            fields.add(S_GenericTupleAttr(fieldName, toExpression(ch as RellManualParser.ExpressionContext), fieldComment))
            i++
        }
        val singleField = getTupleSingleField(fields, trailingComma)
        return if (singleField != null) S_ParenthesesExpr(ctx.start.toPos(), singleField)
        else S_TupleExpr(ctx.start.toPos(), fields.toImmList())
    }

    private fun toCreateExpr(ctx: RellManualParser.CreateExprContext): S_Expr = withCtx(ctx) {
        val kwTok = ctx.start
        val qName = toQualifiedName(ctx.qualifiedName())
        // args: each `(('.'? RULE_ID '=')? ('*' | expression))`
        val children = ctx.children
        val args = mutableListOf<S_CallArgument>()
        // Find opening '(' of args (after qualifiedName).
        val qnIdx = children.indexOfFirst { it is RellManualParser.QualifiedNameContext }
        var i = qnIdx + 1
        while (i < children.size && !(children[i] is TerminalNode && (children[i] as TerminalNode).text == "(")) i++
        val lpar = children[i] as TerminalNode
        i++
        var rparPos = lpar.symbol.toPos()
        while (i < children.size) {
            val ch = children[i]
            if (ch is TerminalNode) {
                when (ch.text) {
                    ")" -> { rparPos = ch.symbol.toPos(); break }
                    "," -> { i++; continue }
                    else -> { i++; continue }
                }
            }
            // Determine arg name and value.
            var argName: S_Name? = null
            // Look back for `('.'? RULE_ID '=')?`
            if (i >= 2) {
                val tn0 = children[i - 1]
                val tn1 = children[i - 2]
                if (tn0 is TerminalNode && tn0.text == "=" && tn1 is TerminalNode
                    && tn1.symbol.type == RellManualParser.RULE_ID) {
                    argName = idTokenToName(tn1)
                }
            }
            val value = if (ch is RellManualParser.ExpressionContext) {
                S_CallArgumentValue_Expr(toExpression(ch))
            } else {
                error("create arg: unexpected child ${ch.javaClass.simpleName}")
            }
            args.add(S_CallArgument(argName, value))
            i++
        }
        // Handle '*' wildcard args: those appear as TerminalNodes in children, not Expression.
        // We need a unified pass — redo, walking and tracking name+value per arg.
        // To keep things consistent, redo from scratch:
        args.clear()
        i = qnIdx + 1
        while (i < children.size && !(children[i] is TerminalNode && (children[i] as TerminalNode).text == "(")) i++
        i++ // past '('
        while (i < children.size) {
            val cur = children[i]
            if (cur is TerminalNode && cur.text == ")") { rparPos = cur.symbol.toPos(); break }
            if (cur is TerminalNode && cur.text == ",") { i++; continue }
            // Try to read `('.'? RULE_ID '=')?` then `('*' | expression)`.
            var argName: S_Name? = null
            // Skip optional '.'
            var j = i
            if (j < children.size && children[j] is TerminalNode && (children[j] as TerminalNode).text == ".") j++
            // optional RULE_ID '='
            if (j + 1 < children.size && children[j] is TerminalNode
                && (children[j] as TerminalNode).symbol.type == RellManualParser.RULE_ID
                && children[j + 1] is TerminalNode
                && (children[j + 1] as TerminalNode).text == "="
            ) {
                argName = idTokenToName(children[j] as TerminalNode)
                j += 2
            }
            // value
            require(j < children.size) { "create arg: missing value" }
            val valChild = children[j]
            val argValue: S_CallArgumentValue = when (valChild) {
                is TerminalNode -> {
                    require(valChild.text == "*") { "create arg: unexpected terminal ${valChild.text}" }
                    S_CallArgumentValue_Wildcard(valChild.symbol.toPos())
                }
                is RellManualParser.ExpressionContext -> S_CallArgumentValue_Expr(toExpression(valChild))
                else -> error("create arg: unexpected ${valChild.javaClass.simpleName}")
            }
            args.add(S_CallArgument(argName, argValue))
            i = j + 1
        }
        return S_CreateExpr(kwTok.toPos(), qName, args.toImmList(), S_PosRange(lpar.symbol.toPos(), rparPos))
    }

    private fun toGenericTypeExpr(ctx: RellManualParser.GenericTypeExprContext): S_Expr = withCtx(ctx) {
        val qName = toQualifiedName(ctx.qualifiedName())
        val typeArgs = ctx.type().map { toType(it) }.toImmList()
        val genType = S_GenericType(qName, typeArgs)
        val baseExpr = S_GenericTypeExpr(genType)
        // Followed by `callArgs | '.' RULE_ID`
        val callArgs = ctx.callArgs()
        if (callArgs != null) {
            return S_CallExpr(baseExpr, toCallArgs(callArgs))
        }
        // member access: '.' RULE_ID
        val ruleId = ctx.RULE_ID()!!
        return S_MemberExpr(baseExpr, idTokenToName(ruleId))
    }

    private fun toTailNoCallNoAt(ctx: RellManualParser.BaseExprTailNoCallNoAtContext): TailDescriptor = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.BaseExprTailMemberContext -> TailDescriptor.Member(idTokenToName(ctx.RULE_ID()))
            is RellManualParser.BaseExprTailSafeMemberContext -> TailDescriptor.SafeMember(idTokenToName(ctx.RULE_ID()))
            is RellManualParser.BaseExprTailSubscriptContext -> {
                val lbrack = ctx.start
                TailDescriptor.Subscript(lbrack.toPos(), toExpression(ctx.expression()))
            }
            is RellManualParser.BaseExprTailNotNullContext -> TailDescriptor.NotNull(ctx.start.toPos())
            is RellManualParser.BaseExprTailUnaryPostfixOpContext -> {
                val tok = ctx.start
                val op: S_UnaryOp = when (tok.text) {
                    "++" -> S_UnaryOp_IncDec(inc = true, post = true)
                    "--" -> S_UnaryOp_IncDec(inc = false, post = true)
                    "??" -> S_UnaryOp_IsNull
                    else -> error("unknown postfix op: ${tok.text}")
                }
                TailDescriptor.PostfixOp(tok.toPos(), op)
            }
            else -> error("unknown tailNoCallNoAt: ${ctx.javaClass.simpleName}")
        }
    }

    private fun applyTail(base: S_Expr, tail: TailDescriptor): S_Expr {
        return when (tail) {
            is TailDescriptor.Member -> S_MemberExpr(base, tail.name)
            is TailDescriptor.SafeMember -> S_SafeMemberExpr(base, tail.name)
            is TailDescriptor.Subscript -> S_SubscriptExpr(tail.opPos, base, tail.expr)
            is TailDescriptor.NotNull -> S_UnaryExpr(base.startPos, S_PosValue(tail.opPos, S_UnaryOp_NotNull), base)
            is TailDescriptor.PostfixOp -> S_UnaryExpr(base.startPos, S_PosValue(tail.opPos, tail.op), base)
        }
    }

    private fun applyTails(base: S_Expr, tails: List<TailDescriptor>): S_Expr {
        var res = base
        for (t in tails) res = applyTail(res, t)
        return res
    }

    private fun toCallArgs(ctx: RellManualParser.CallArgsContext): S_CallArguments = withCtx(ctx) {
        // callArgs: '(' (((RULE_ID '=')? ('*' | expression)) (',' ...)* ','?)? ')'
        val children = ctx.children
        val args = mutableListOf<S_CallArgument>()
        val lpar = children[0] as TerminalNode
        var i = 1
        var rparPos = lpar.symbol.toPos()
        while (i < children.size) {
            val cur = children[i]
            if (cur is TerminalNode && cur.text == ")") { rparPos = cur.symbol.toPos(); break }
            if (cur is TerminalNode && cur.text == ",") { i++; continue }
            var argName: S_Name? = null
            var j = i
            // optional RULE_ID '='
            if (j + 1 < children.size && children[j] is TerminalNode
                && (children[j] as TerminalNode).symbol.type == RellManualParser.RULE_ID
                && children[j + 1] is TerminalNode
                && (children[j + 1] as TerminalNode).text == "="
            ) {
                argName = idTokenToName(children[j] as TerminalNode)
                j += 2
            }
            val valChild = children[j]
            val argValue: S_CallArgumentValue = when (valChild) {
                is TerminalNode -> {
                    require(valChild.text == "*") { "callArg: unexpected terminal ${valChild.text}" }
                    S_CallArgumentValue_Wildcard(valChild.symbol.toPos())
                }
                is RellManualParser.ExpressionContext -> S_CallArgumentValue_Expr(toExpression(valChild))
                else -> error("callArg: unexpected ${valChild.javaClass.simpleName}")
            }
            args.add(S_CallArgument(argName, argValue))
            i = j + 1
        }
        return S_CallArguments(args.toImmList(), S_PosRange(lpar.symbol.toPos(), rparPos))
    }

    private fun toAtCardinality(ctx: RellManualParser.AtExprAtContext): S_PosValue<AtCardinality> = withCtx(ctx) {
        val atTok = ctx.start
        val pos = atTok.toPos()
        // Look at second token if present.
        val second = ctx.children.getOrNull(1)
        val card = if (second is TerminalNode) when (second.text) {
            "?" -> AtCardinality.ZERO_ONE
            "*" -> AtCardinality.ZERO_MANY
            "+" -> AtCardinality.ONE_MANY
            else -> AtCardinality.ONE
        } else AtCardinality.ONE
        return S_PosValue(pos, card)
    }

    private fun toAtExprWhere(ctx: RellManualParser.AtExprWhereContext): S_AtExprWhere = withCtx(ctx) {
        val lcurl = ctx.start
        val rcurl = ctx.stop
        val exprs = ctx.expression().map { toExpression(it) }.toImmList()
        return S_AtExprWhere(exprs, S_PosRange(lcurl.toPos(), rcurl.toPos()))
    }

    private fun toAtExprWhat(ctx: RellManualParser.AtExprWhatContext): S_AtExprWhat = withCtx(ctx) {
        return when (ctx) {
            is RellManualParser.AtExprWhatSimpleContext -> {
                val ids = ctx.RULE_ID()
                val path = ids.map { idTokenToName(it) }.toImmList()
                val dotTok = ctx.start
                S_AtExprWhat_Simple(dotTok.toPos(), path)
            }
            is RellManualParser.AtExprWhatComplexContext -> {
                // walk children to collect fields
                val fields = parseAtExprWhatComplexFields(ctx)
                val lpar = ctx.start
                val rpar = ctx.stop
                S_AtExprWhat_Complex(S_PosRange(lpar.toPos(), rpar.toPos()), fields.toImmList())
            }
            else -> error("unknown atExprWhat: ${ctx.javaClass.simpleName}")
        }
    }

    private fun parseAtExprWhatComplexFields(
        ctx: RellManualParser.AtExprWhatComplexContext,
    ): List<S_AtExprWhatComplexField> {
        // Children: '(' (annotation* (RULE_ID '=')? expression) (',' annotation* (RULE_ID '=')? expression)* ','? ')'
        val children = ctx.children
        val fields = mutableListOf<S_AtExprWhatComplexField>()
        var i = 1 // skip '('
        while (i < children.size) {
            val cur = children[i]
            // Skip separators / closing paren. Other terminals (RULE_ID for explicit field name) fall through.
            if (cur is TerminalNode) {
                if (cur.text == ")") break
                if (cur.text == ",") { i++; continue }
            }
            // Collect annotations until RULE_ID '=' or expression
            val anns = mutableListOf<S_Annotation>()
            var j = i
            while (j < children.size && children[j] is RellManualParser.AnnotationContext) {
                anns.add(toAnnotation(children[j] as RellManualParser.AnnotationContext))
                j++
            }
            // optional RULE_ID '='
            var attrName: S_Name? = null
            var attrNameTokIdx = -1
            if (j + 1 < children.size && children[j] is TerminalNode
                && (children[j] as TerminalNode).symbol.type == RellManualParser.RULE_ID
                && children[j + 1] is TerminalNode
                && (children[j + 1] as TerminalNode).text == "="
            ) {
                attrNameTokIdx = j
                attrName = idTokenToName(children[j] as TerminalNode)
                j += 2
            }
            // expression
            val exprCtx = children[j] as RellManualParser.ExpressionContext
            val expr = toExpression(exprCtx)
            val sMods = if (anns.isEmpty()) S_Modifiers() else S_Modifiers(anns.toImmList())
            // Match grammar.kt: doc-comment from the first annotation or the name token (none if neither).
            val firstTok: Token? = when {
                anns.isNotEmpty() -> (children[i] as RellManualParser.AnnotationContext).start
                attrNameTokIdx >= 0 -> (children[attrNameTokIdx] as TerminalNode).symbol
                else -> null
            }
            val fieldComment = firstTok?.let { docCommentForToken(it) }
            fields.add(S_AtExprWhatComplexField(attrName, expr, sMods, null, fieldComment))
            i = j + 1
        }
        return fields
    }

    private fun toAtExprModifiers(ctx: RellManualParser.AtExprModifiersContext): Pair<S_Expr?, S_Expr?> = withCtx(ctx) {
        // Two shapes:
        //  - 'limit' expression ('offset' expression)?
        //  - 'offset' expression ('limit' expression)?
        var limit: S_Expr? = null
        var offset: S_Expr? = null
        val children = ctx.children
        var i = 0
        while (i < children.size) {
            val ch = children[i]
            if (ch is TerminalNode) {
                when (ch.text) {
                    "limit" -> {
                        limit = toExpression(children[i + 1] as RellManualParser.ExpressionContext)
                        i += 2
                    }
                    "offset" -> {
                        offset = toExpression(children[i + 1] as RellManualParser.ExpressionContext)
                        i += 2
                    }
                    else -> i++
                }
            } else i++
        }
        return Pair(limit, offset)
    }

    private fun parseAtExprFromItems(ctx: RellManualParser.AtExprContext): List<S_AtExprFromItem> = withCtx(ctx) {
        // Children: '(' (annotation* (RULE_ID ':')? expression) (',' annotation* (RULE_ID ':')? expression)* ','? ')'
        // followed by atExprAt atExprWhere atExprWhat? atExprModifiers?
        val children = ctx.children
        val items = mutableListOf<S_AtExprFromItem>()
        var i = 1 // skip '('
        while (i < children.size) {
            val cur = children[i]
            // Skip separators / closing paren. Other terminals (RULE_ID for alias) fall through.
            if (cur is TerminalNode) {
                if (cur.text == ")") break
                if (cur.text == ",") { i++; continue }
            }
            // Past the atExpr's `)`: subsequent children belong to the at-tail (atExprAt etc.) — bail.
            if (cur is RellManualParser.AtExprAtContext) break
            val anns = mutableListOf<S_Annotation>()
            var j = i
            while (j < children.size && children[j] is RellManualParser.AnnotationContext) {
                anns.add(toAnnotation(children[j] as RellManualParser.AnnotationContext))
                j++
            }
            var alias: S_Name? = null
            var aliasTokIdx = -1
            if (j + 1 < children.size && children[j] is TerminalNode
                && (children[j] as TerminalNode).symbol.type == RellManualParser.RULE_ID
                && children[j + 1] is TerminalNode
                && (children[j + 1] as TerminalNode).text == ":"
            ) {
                aliasTokIdx = j
                alias = idTokenToName(children[j] as TerminalNode)
                j += 2
            }
            val exprCtx = children[j] as RellManualParser.ExpressionContext
            val expr = toExpression(exprCtx)
            val sMods = if (anns.isEmpty()) S_Modifiers() else S_Modifiers(anns.toImmList())
            // Match grammar.kt: comment from first annotation or alias token (none if neither).
            val firstTok: Token? = when {
                anns.isNotEmpty() -> (children[i] as RellManualParser.AnnotationContext).start
                aliasTokIdx >= 0 -> (children[aliasTokIdx] as TerminalNode).symbol
                else -> null
            }
            val itemComment = firstTok?.let { docCommentForToken(it) }
            items.add(S_AtExprFromItem(sMods, alias, expr, itemComment))
            i = j + 1
        }
        if (items.isEmpty()) {
            // Error-recovery path: ANTLR may produce an `AtExprContext` whose `(...)` parens are
            // empty (or contain only synthesized error tokens). Synthesize a placeholder item so
            // `parseWithErrors` can return a usable partial AST instead of throwing.
            val pos = ctx.start?.toPos() ?: errorPos()
            val placeholder = S_NameExpr(S_QualifiedName(immListOf(S_Name(pos, PLACEHOLDER_NAME))))
            items.add(S_AtExprFromItem(S_Modifiers(), null, placeholder, null))
        }
        return items
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers

    private fun toQualifiedName(ctx: RellManualParser.QualifiedNameContext): S_QualifiedName = withCtx(ctx) {
        val ids = ctx.RULE_ID()
        if (ids.isEmpty()) {
            // Error-recovery: empty qualifiedName context (parser inserted a placeholder).
            // Synthesize a single placeholder S_Name so downstream code has something to
            // anchor on instead of crashing on an empty parts list.
            val pos = ctx.start?.toPos() ?: errorPos()
            return S_QualifiedName(immListOf(S_Name(pos, PLACEHOLDER_NAME)))
        }
        val parts = ids.map { idTokenToName(it) }.toImmList()
        return S_QualifiedName(parts)
    }

    /** Null-tolerant variant: when error recovery omits the qualifiedName entirely. */
    private fun toQualifiedNameOrPlaceholder(
        ctx: RellManualParser.QualifiedNameContext?,
        anchor: ParserRuleContext,
    ): S_QualifiedName {
        if (ctx != null) return toQualifiedName(ctx)
        val pos = anchor.start?.toPos() ?: errorPos()
        return S_QualifiedName(immListOf(S_Name(pos, PLACEHOLDER_NAME)))
    }

    private fun errorPos(): S_Pos {
        // Fallback for truly synthetic positions: use the file's first token if available.
        // Used only on extremely degenerate trees; production paths always have tokens.
        return S_BasicPos(filePath, 0, 1, 1)
    }

    private fun idTokenToName(tk: TerminalNode): S_Name {
        // Tolerate ANTLR error-recovery TerminalNodes (synthetic "missing RULE_ID" tokens
        // whose text is not a valid identifier). Synthesize a placeholder so the visitor
        // can keep building a partial AST for IDE features.
        val text = tk.text
        if (text == null || !Name.isValid(text)) {
            val pos = tk.symbol?.toPos() ?: errorPos()
            return S_Name(pos, PLACEHOLDER_NAME)
        }
        if (!attachmentMode) {
            val pos = tk.symbol.toPos()
            return S_Name(pos, RellTokenizer.decodeName(pos, tk.text))
        }
        // Push a synthetic single-token ctx so the S_Name's attachment + position scope is
        // exactly the identifier token (consumers do `pos.node.text` / `attachment.node.start..stop`
        // and expect identifier-sized values, not the enclosing def's span).
        val tokenCtx = TokenRuleContext(tk.symbol)
        return withCtx(tokenCtx) {
            val pos = tk.symbol.toPos()
            S_Name(pos, RellTokenizer.decodeName(pos, tk.text))
        }
    }

    private fun Token.toPos(): S_Pos {
        // ANTLR: line is 1-based; charPositionInLine is 0-based; S_BasicPos uses 1-based for col.
        if (attachmentMode) {
            val ctx = contextStack.last()
            // If the top-of-stack already wraps exactly this token, no override needed.
            val override = if (ctx is TokenRuleContext && ctx.token === this) null else this
            return AntlrPos(ctx, filePath.sourcePath, filePath.idePath, tokenOverride = override)
        }
        return S_BasicPos(filePath, this.startIndex, this.line, this.charPositionInLine + 1)
    }

    /** Synthetic single-token ParserRuleContext: lets `attachment.node.text/start/stop` reflect just one token. */
    private class TokenRuleContext(val token: Token) : ParserRuleContext() {
        init {
            start = token
            stop = token
        }

        override fun getText(): String = token.text ?: ""
    }

    private fun assignOpCode(text: String): S_AssignOpCode = when (text) {
        "=" -> S_AssignOpCode.EQ
        "+=" -> S_AssignOpCode.PLUS
        "-=" -> S_AssignOpCode.MINUS
        "*=" -> S_AssignOpCode.MUL
        "/=" -> S_AssignOpCode.DIV
        "%=" -> S_AssignOpCode.MOD
        else -> error("unknown assign op: $text")
    }

    private fun <T> getTupleSingleField(items: List<S_GenericTupleAttr<T>>, trailingComma: Boolean): T? {
        if (items.size != 1) return null
        if (trailingComma) return null
        val first = items[0]
        if (first.name != null) return null
        return first.value
    }

    /**
     * Decode an ANTLR RULE_STRING token. ANTLR captures the raw source text including quotes and
     * unprocessed escape sequences; we strip the quotes and decode escapes ourselves.
     */
    private fun decodeStringTokenText(pos: S_Pos, raw: String): String {
        require(raw.length >= 2) { "bad string literal: $raw" }
        val inner = raw.substring(1, raw.length - 1)
        // Decode escapes.
        val sb = StringBuilder(inner.length)
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c != '\\') {
                sb.append(c)
                i++
                continue
            }
            require(i + 1 < inner.length) { "bad escape at end of string: $raw" }
            when (val n = inner[i + 1]) {
                'b' -> { sb.append('\b'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                'f' -> { sb.append(''); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                '"' -> { sb.append('"'); i += 2 }
                '\'' -> { sb.append('\''); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                'u' -> {
                    require(i + 6 <= inner.length) { "bad unicode escape: $raw" }
                    val hex = inner.substring(i + 2, i + 6)
                    sb.append(hex.toInt(16).toChar())
                    i += 6
                }
                else -> error("bad escape: \\$n")
            }
        }
        // RellTokenizer.decodeString is now identity but we go through it for parity.
        return RellTokenizer.decodeString(pos, sb.toString())
    }

    /**
     * Decode an ANTLR RULE_BYTES token, stripping the `x'...'` or `x"..."` wrapper to get the
     * raw hex content that `RellTokenizer.decodeByteArray` expects.
     */
    private fun decodeBytesTokenText(raw: String): String {
        // raw is `x'HEX'` or `x"HEX"`.
        require(raw.length >= 3) { "bad bytes literal: $raw" }
        // Strip the leading `x` and the surrounding quotes.
        return raw.substring(2, raw.length - 1)
    }

    // Look for a doc-comment (text starts with `/` followed by two or more `*`, ends with star-slash,
    // length >= 5) immediately preceding the start of [ctx]. Walks backward from `ctx.start` through
    // hidden-channel tokens (whitespace, line comments) and channel-2 tokens (multiline comments).
    // Returns null when there is no token stream, no preceding comment, or the candidate is not a
    // doc comment (e.g. the empty `/* */` form which is length 4, or a normal `/* ... */` block).
    private fun docCommentFor(ctx: ParserRuleContext): S_Comment? {
        val startTok = ctx.start ?: return null
        return docCommentForToken(startTok)
    }

    private fun docCommentForToken(startTok: Token): S_Comment? {
        val tokens = tokenStream ?: return null
        val startIdx = startTok.tokenIndex
        if (startIdx <= 0) return null
        var i = startIdx - 1
        while (i >= 0) {
            val tok = tokens.get(i)
            when (tok.channel) {
                Lexer.HIDDEN -> { i--; continue }
                2 -> {
                    val text = tok.text ?: return null
                    if (!text.startsWith("/**")) return null
                    if (text.length < 5) return null
                    if (!text.endsWith("*/")) return null
                    val pos = S_BasicPos(filePath, tok.startIndex, tok.line, tok.charPositionInLine + 1)
                    return S_Comment(pos, text)
                }
                else -> return null
            }
        }
        return null
    }

    // Tail descriptor for chained baseExpr suffixes.
    private sealed class TailDescriptor {
        class Member(val name: S_Name) : TailDescriptor()
        class SafeMember(val name: S_Name) : TailDescriptor()
        class Subscript(val opPos: S_Pos, val expr: S_Expr) : TailDescriptor()
        class NotNull(val opPos: S_Pos) : TailDescriptor()
        class PostfixOp(val opPos: S_Pos, val op: S_UnaryOp) : TailDescriptor()
    }

    companion object {
        /** Placeholder identifier used when ANTLR error recovery omits a name token. */
        private val PLACEHOLDER_NAME = Name.of("_")

        private val ASSIGN_OP_TEXTS = setOf("=", "+=", "-=", "*=", "/=", "%=")
        private val PREFIX_OP_TEXTS = setOf("+", "-", "not", "++", "--")

        private val BIN_OP_BY_SYMBOL: Map<String, S_BinaryOp> = mapOf(
            "==" to S_BinaryOp.EQ,
            "!=" to S_BinaryOp.NE,
            "<=" to S_BinaryOp.LE,
            ">=" to S_BinaryOp.GE,
            "<" to S_BinaryOp.LT,
            ">" to S_BinaryOp.GT,
            "===" to S_BinaryOp.EQ_REF,
            "!==" to S_BinaryOp.NE_REF,
            "+" to S_BinaryOp.PLUS,
            "-" to S_BinaryOp.MINUS,
            "*" to S_BinaryOp.MUL,
            "/" to S_BinaryOp.DIV,
            "%" to S_BinaryOp.MOD,
            "and" to S_BinaryOp.AND,
            "or" to S_BinaryOp.OR,
            "&" to S_BinaryOp.AMPERSAND,
            "in" to S_BinaryOp.IN,
            "?:" to S_BinaryOp.ELVIS,
        )
    }
}

