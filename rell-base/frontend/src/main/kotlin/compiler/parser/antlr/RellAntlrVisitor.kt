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
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Converts ANTLR `RellParser` parse trees into Rell's compiler `S_*` AST.
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

    fun toFile(ctx: RellParser.FileContext): S_RellFile = withAttachmentScope {
        withCtx(ctx) {
            val header = ctx.moduleHeader()?.let { toModuleHeader(it) }
            val defs = ctx.annotatedDef().map { toAnnotatedDef(it) }.toImmList()
            S_RellFile(header, defs)
        }
    }

    fun toReplCommand(ctx: RellParser.ReplCommandContext): S_ReplCommand = withAttachmentScope {
        withCtx(ctx) {
            val steps = ctx.replStep().map { toReplStep(it) }
            val expr = ctx.expression()?.let { toExpression(it) }
            S_ReplCommand(steps, expr)
        }
    }

    private fun toReplStep(ctx: RellParser.ReplStepContext): S_ReplStep = withCtx(ctx) {
        when (ctx) {
            is RellParser.DefReplStepContext -> {
                val mods = toModifiers(ctx.modifiers())
                val def = toReplDef(ctx.replDef(), mods, ctx)
                S_DefinitionReplStep(def)
            }
            is RellParser.StmtReplStepContext -> S_StatementReplStep(toStatement(ctx.statement()))
            is RellParser.ExprReplStepContext -> {
                val expr = toExpression(ctx.expression())
                val semiTok = ctx.children.last { it is TerminalNode && it.text == ";" } as TerminalNode
                S_StatementReplStep(S_ExprStatement(expr, semiTok.symbol.toPos()))
            }
            else -> error("unknown replStep alt: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toReplDef(
        ctx: RellParser.ReplDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        toDefAlternative(
            modifiers,
            outerCtx,
            entityDef = ctx.entityDef(),
            objectDef = ctx.objectDef(),
            structDef = ctx.structDef(),
            enumDef = ctx.enumDef(),
            functionDef = ctx.functionDef(),
            namespaceDef = ctx.namespaceDef(),
            importDef = ctx.importDef(),
            opDef = ctx.opDef(),
            queryDef = ctx.queryDef(),
            includeDef = ctx.includeDef(),
        )
    }

    fun toModuleHeader(ctx: RellParser.ModuleHeaderContext): S_ModuleHeader = withCtx(ctx) {
        val mods = toModifiers(ctx.modifiers())
        val moduleKwTok = ctx.children.first { it is TerminalNode && it.text == "module" } as TerminalNode
        return S_ModuleHeader(mods, moduleKwTok.symbol.toPos(), docCommentFor(ctx))
    }

    // ---------------------------------------------------------------------------------------------
    // Modifiers / annotations

    private fun toModifiers(ctx: RellParser.ModifiersContext): S_Modifiers = withCtx(ctx) {
        val mods = ctx.modifier().map { toModifier(it) }
        return if (mods.isEmpty()) S_Modifiers() else S_Modifiers(mods.toImmList())
    }

    private fun toModifier(ctx: RellParser.ModifierContext): S_Modifier = withCtx(ctx) {
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

    private fun toAnnotation(ctx: RellParser.AnnotationContext): S_Annotation = withCtx(ctx) {
        val nameTok = ctx.RULE_ID()
        // Same synthetic single-token scope as idTokenToName: without it the name's position node
        // would be the whole annotation, so consumers that measure `pos.node` would treat every
        // token of `@mount('ft4')` as part of the annotation name.
        val name = withCtx(TokenRuleContext(nameTok.symbol)) {
            S_Name(nameTok.symbol.toPos(), Name.of(nameTok.text))
        }
        val args = ctx.annotationArgs()?.annotationArg()?.map { toAnnotationArg(it) } ?: emptyList()
        return S_Annotation(name, args.toImmList())
    }

    private fun toAnnotationArg(ctx: RellParser.AnnotationArgContext): S_AnnotationArg = withCtx(ctx) {
        ctx.qualifiedName()?.let { return S_AnnotationArg_Name(toQualifiedName(it)) }
        // Otherwise it must be a literal token
        val lit = literalFromAnnotationArg(ctx)
        return S_AnnotationArg_Value(lit)
    }

    private fun literalFromAnnotationArg(ctx: RellParser.AnnotationArgContext): S_LiteralExpr = withCtx(ctx) {
        ctx.RULE_NUMBER()?.let { return integerLiteral(it) }
        ctx.RULE_BIG_INTEGER()?.let { return bigIntegerLiteral(it) }
        ctx.RULE_DECIMAL()?.let { return decimalLiteral(it) }
        ctx.RULE_STRING()?.let { return stringLiteral(it) }
        ctx.RULE_BYTES()?.let { return byteArrayLiteral(it) }
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

    fun toAnnotatedDef(ctx: RellParser.AnnotatedDefContext): S_Definition = withCtx(ctx) {
        val mods = toModifiers(ctx.modifiers())
        return toAnyDef(ctx.anyDef(), mods, ctx)
    }

    private fun toAnyDef(
        ctx: RellParser.AnyDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        toDefAlternative(
            modifiers,
            outerCtx,
            entityDef = ctx.entityDef(),
            objectDef = ctx.objectDef(),
            structDef = ctx.structDef(),
            enumDef = ctx.enumDef(),
            functionDef = ctx.functionDef(),
            namespaceDef = ctx.namespaceDef(),
            importDef = ctx.importDef(),
            opDef = ctx.opDef(),
            queryDef = ctx.queryDef(),
            includeDef = ctx.includeDef(),
            constantDef = ctx.constantDef(),
        )
    }

    /**
     * Dispatches over the definition alternatives shared by `anyDef` and `replDef` (the latter has
     * no `constantDef`). Exactly one of the arguments is non-null in a well-formed parse tree.
     */
    private fun toDefAlternative(
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
        entityDef: RellParser.EntityDefContext?,
        objectDef: RellParser.ObjectDefContext?,
        structDef: RellParser.StructDefContext?,
        enumDef: RellParser.EnumDefContext?,
        functionDef: RellParser.FunctionDefContext?,
        namespaceDef: RellParser.NamespaceDefContext?,
        importDef: RellParser.ImportDefContext?,
        opDef: RellParser.OpDefContext?,
        queryDef: RellParser.QueryDefContext?,
        includeDef: RellParser.IncludeDefContext?,
        constantDef: RellParser.ConstantDefContext? = null,
    ): S_Definition {
        entityDef?.let { return toEntityDef(it, modifiers, outerCtx) }
        objectDef?.let { return toObjectDef(it, modifiers, outerCtx) }
        structDef?.let { return toStructDef(it, modifiers, outerCtx) }
        enumDef?.let { return toEnumDef(it, modifiers, outerCtx) }
        functionDef?.let { return toFunctionDef(it, modifiers, outerCtx) }
        namespaceDef?.let { return toNamespaceDef(it, modifiers, outerCtx) }
        importDef?.let { return toImportDef(it, modifiers, outerCtx) }
        opDef?.let { return toOpDef(it, modifiers, outerCtx) }
        queryDef?.let { return toQueryDef(it, modifiers, outerCtx) }
        includeDef?.let { return toIncludeDef(it, modifiers, outerCtx) }
        constantDef?.let { return toConstantDef(it, modifiers, outerCtx) }
        error("unknown definition alt")
    }

    private fun makeDefBase(kwToken: Token, modifiers: S_Modifiers, outerCtx: ParserRuleContext): S_DefinitionBase {
        return S_DefinitionBase(kwToken.toPos(), modifiers, docCommentFor(outerCtx))
    }

    private fun toEntityDef(
        ctx: RellParser.EntityDefContext,
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

    private fun toEntityBody(ctx: RellParser.EntityBodyContext?): ImmList<S_RelClause>? {
        if (ctx == null) return null
        // body alts: ';' (no clauses) or '{' relClause* '}'
        // The semicolon-only form is `null` body in the original grammar.kt (entityBodyShort).
        val firstChild = ctx.children?.firstOrNull()
        if (firstChild is TerminalNode && firstChild.text == ";") {
            return null
        }
        return ctx.relClause().map { toRelClause(it) }.toImmList()
    }

    private fun toRelClause(ctx: RellParser.RelClauseContext): S_RelClause = withCtx(ctx) {
        ctx.attributeClause()?.let { return toAttributeClause(it) }
        ctx.keyIndexClause()?.let { return toKeyIndexClause(it) }
        error("unknown relClause")
    }

    private fun toAttributeClause(ctx: RellParser.AttributeClauseContext): S_AttributeClause = withCtx(ctx) {
        val attr = toAttributeDefinition(ctx.baseAttributeDefinition())
        return S_AttributeClause(attr, docCommentFor(ctx))
    }

    private fun toKeyIndexClause(ctx: RellParser.KeyIndexClauseContext): S_KeyIndexClause = withCtx(ctx) {
        val kwTok = ctx.start
        val kind = when (kwTok.text) {
            "key" -> KeyIndexKind.KEY
            "index" -> KeyIndexKind.INDEX
            else -> error("unknown key/index keyword: ${kwTok.text}")
        }
        val attrs = ctx.baseAttributeDefinition().map { toAttributeDefinition(it) }.toImmList()
        return S_KeyIndexClause(kwTok.toPos(), kind, attrs, docCommentForToken(kwTok))
    }

    private fun toAttributeDefinition(ctx: RellParser.BaseAttributeDefinitionContext): S_AttributeDefinition = withCtx(ctx) {
        val mods = toModifiers(ctx.modifiers())
        val header = toAttrHeader(ctx.attrHeader())
        val expr = ctx.expression()?.let { toExpression(it) }
        return S_AttributeDefinition(mods, header, expr)
    }

    private fun toAttrHeader(ctx: RellParser.AttrHeaderContext): S_AttrHeader = withCtx(ctx) {
        return when (ctx) {
            is RellParser.NameTypeAttrHeaderContext -> {
                val name = idTokenToName(ctx.RULE_ID())
                S_NamedAttrHeader(name, toType(ctx.type()))
            }
            is RellParser.AnonAttrHeaderContext -> {
                val qName = toQualifiedName(ctx.qualifiedName())
                val nullable = ctx.children.any { it is TerminalNode && it.text == "?" }
                S_AnonAttrHeader(qName, nullable)
            }
            else -> error("unknown attrHeader: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toObjectDef(
        ctx: RellParser.ObjectDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        val name = idTokenToName(ctx.RULE_ID())
        val attrs = ctx.attributeClause().map { toAttributeClause(it) }.toImmList()
        return S_ObjectDefinition(makeDefBase(kwTok, modifiers, outerCtx), name, attrs)
    }

    private fun toStructDef(
        ctx: RellParser.StructDefContext,
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
        ctx: RellParser.EnumDefContext,
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
        ctx: RellParser.FunctionDefContext,
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

    private fun toFormalParameters(ctx: RellParser.FormalParametersContext?): ImmList<S_FormalParameter> {
        if (ctx == null) return immListOf()
        return withCtx(ctx) {
            ctx.formalParameter().map { toFormalParameter(it) }.toImmList()
        }
    }

    private fun toFormalParameter(ctx: RellParser.FormalParameterContext): S_FormalParameter = withCtx(ctx) {
        val mods = toModifiers(ctx.modifiers())
        val attr = toAttrHeader(ctx.attrHeader())
        val expr = ctx.expression()?.let { toExpression(it) }
        return S_FormalParameter(mods, attr, expr, docCommentFor(ctx))
    }

    private fun toFunctionBody(ctx: RellParser.FunctionBodyContext): S_FunctionBody? = withCtx(ctx) {
        val firstChild = ctx.children.first()
        if (firstChild is TerminalNode) {
            return when (firstChild.text) {
                ";" -> null
                "=" -> toShortFunctionBody(ctx, firstChild)
                else -> error("unknown functionBody first token: ${firstChild.text}")
            }
        }
        // blockStmt
        val stmt = toBlockStatement(ctx.blockStmt()!!)
        return S_FunctionBodyFull(stmt)
    }

    private fun toQueryBody(ctx: RellParser.QueryBodyContext): S_FunctionBody = withCtx(ctx) {
        val firstChild = ctx.children.first()
        if (firstChild is TerminalNode && firstChild.text == "=") {
            return toShortFunctionBody(ctx, firstChild)
        }
        val stmt = toBlockStatement(ctx.blockStmt()!!)
        return S_FunctionBodyFull(stmt)
    }

    /** Expression body of a function or a query: `'=' expression ';'`, [eqNode] being the `'='`. */
    private fun toShortFunctionBody(ctx: ParserRuleContext, eqNode: TerminalNode): S_FunctionBodyShort {
        val exprCtx = ctx.getRuleContext(RellParser.ExpressionContext::class.java, 0)
        val expr = toExpression(exprCtx)
        val endTok = ctx.children.last { it is TerminalNode && it.text == ";" } as TerminalNode
        return S_FunctionBodyShort(S_PosRange(eqNode.symbol.toPos(), endTok.symbol.toPos()), expr)
    }

    private fun toNamespaceDef(
        ctx: RellParser.NamespaceDefContext,
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
        ctx: RellParser.ImportDefContext,
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
            S_ImportModulePath(null, placeholderName(pos))
        }
        val target = ctx.importTarget()?.let { toImportTarget(it) } ?: S_DefaultImportTarget
        return S_ImportDefinition(makeDefBase(kwTok, modifiers, outerCtx), alias, module, target)
    }

    private fun toImportModule(ctx: RellParser.ImportModuleContext): S_ImportModulePath = withCtx(ctx) {
        return when (ctx) {
            is RellParser.AbsoluteImportModuleContext -> {
                S_ImportModulePath(null, toQualifiedName(ctx.qualifiedName()))
            }
            is RellParser.RelativeImportModuleContext -> {
                val dot = ctx.children.first { it is TerminalNode && it.text == "." } as TerminalNode
                val qName = ctx.qualifiedName()?.let { toQualifiedName(it) }
                S_ImportModulePath(S_RelativeImportModulePath(dot.symbol.toPos(), 0), qName)
            }
            is RellParser.UpImportModuleContext -> {
                val carets = ctx.children.filter { it is TerminalNode && it.text == "^" }
                val firstCaret = carets.first() as TerminalNode
                val qName = ctx.qualifiedName()?.let { toQualifiedName(it) }
                S_ImportModulePath(S_RelativeImportModulePath(firstCaret.symbol.toPos(), carets.size), qName)
            }
            else -> error("unknown importModule: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toImportTarget(ctx: RellParser.ImportTargetContext): S_ImportTarget = withCtx(ctx) {
        ctx.importTargetExact()?.let { exact ->
            val items = exact.importTargetExactItem().map { toImportTargetExactItem(it) }.toImmList()
            return S_ExactImportTarget(items)
        }
        // wildcard '*'
        return S_WildcardImportTarget
    }

    private fun toImportTargetExactItem(ctx: RellParser.ImportTargetExactItemContext): S_ExactImportTargetItem = withCtx(ctx) {
        // Generated `RULE_ID()` returns only the directly-owned alias token (qualifiedName has its own).
        val alias = ctx.RULE_ID()?.let { idTokenToName(it) }
        // Tolerate ANTLR error recovery: qualifiedName() may be null when the parser inserted a
        // missing-rule placeholder. Synthesize an empty qualified-name anchored at the item's start.
        val name = toQualifiedNameOrPlaceholder(ctx.qualifiedName(), ctx)
        val wildcard = ctx.children?.any { it is TerminalNode && it.text == "*" } ?: false
        return S_ExactImportTargetItem(alias, name, wildcard, docCommentFor(ctx))
    }

    private fun toOpDef(
        ctx: RellParser.OpDefContext,
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
        ctx: RellParser.QueryDefContext,
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
        ctx: RellParser.IncludeDefContext,
        modifiers: S_Modifiers,
        outerCtx: ParserRuleContext,
    ): S_Definition = withCtx(ctx) {
        val kwTok = ctx.start
        return S_IncludeDefinition(makeDefBase(kwTok, modifiers, outerCtx))
    }

    private fun toConstantDef(
        ctx: RellParser.ConstantDefContext,
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

    fun toType(ctx: RellParser.TypeContext): S_Type = withCtx(ctx) {
        return when (ctx) {
            is RellParser.FunctionTypeContext -> {
                val types = ctx.type()
                val params = types.dropLast(1).map { toType(it) }.toImmList()
                val result = toType(types.last())
                val startPos = ctx.start.toPos()
                S_FunctionType(startPos, params, result)
            }
            is RellParser.BasicTypeAltContext -> {
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

    private fun toPrimaryType(ctx: RellParser.PrimaryTypeContext): S_Type = withCtx(ctx) {
        return when (ctx) {
            is RellParser.GenericOrNameTypeContext -> {
                val qName = toQualifiedName(ctx.qualifiedName())
                val typeArgs = ctx.type()
                if (typeArgs.isEmpty()) {
                    S_NameType(qName)
                } else {
                    S_GenericType(qName, typeArgs.map { toType(it) }.toImmList())
                }
            }
            is RellParser.TupleTypeContext -> {
                // '(' (RULE_ID ':')? type (',' (RULE_ID ':')? type)* ','? ')'
                val fields = parseTupleFields(ctx, ":") { toType(it as RellParser.TypeContext) }
                fields.singleField ?: S_TupleType(ctx.start.toPos(), fields.list)
            }
            is RellParser.VirtualTypeContext -> {
                val kwTok = ctx.start
                S_VirtualType(kwTok.toPos(), toType(ctx.type()))
            }
            is RellParser.MirrorStructTypeContext -> {
                val kwTok = ctx.start
                val mutable = ctx.children.any { it is TerminalNode && it.text == "mutable" }
                S_MirrorStructType(kwTok.toPos(), mutable, toType(ctx.type()))
            }
            else -> error("unknown primaryType: ${ctx.javaClass.simpleName}")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Statements

    fun toStatement(ctx: RellParser.StatementContext): S_Statement = withCtx(ctx) {
        return when (ctx) {
            is RellParser.EmptyStmtContext -> S_EmptyStatement(ctx.start.toPos())
            is RellParser.VarStmtAltContext -> toVarStatement(ctx)
            is RellParser.ReturnStmtAltContext -> {
                val kwTok = ctx.start
                val expr = ctx.expression()?.let { toExpression(it) }
                val end = ctx.stop
                S_ReturnStatement(kwTok.toPos(), end.toPos(), expr)
            }
            is RellParser.BlockStmtAltContext -> toBlockStatement(ctx.blockStmt())
            is RellParser.IfStmtAltContext -> {
                val kwTok = ctx.start
                val cond = toExpression(ctx.expression())
                val stmts = ctx.statement()
                val trueStmt = toStatement(stmts[0])
                val falseStmt = if (stmts.size > 1) toStatement(stmts[1]) else null
                S_IfStatement(kwTok.toPos(), cond, trueStmt, falseStmt)
            }
            is RellParser.WhenStmtAltContext -> {
                val kwTok = ctx.start
                val expr = ctx.expression()?.let { toExpression(it) }
                val conds = ctx.whenCondition().map { toWhenCondition(it) }
                val stmts = ctx.statement().map { toStatement(it) }
                val cases = conds.zip(stmts) { c, s -> S_WhenStatementCase(c, s) }.toImmList()
                val end = ctx.stop
                S_WhenStatement(kwTok.toPos(), end.toPos(), expr, cases)
            }
            is RellParser.WhileStmtAltContext -> {
                val kwTok = ctx.start
                val expr = toExpression(ctx.expression())
                val stmt = toStatement(ctx.statement())
                S_WhileStatement(kwTok.toPos(), expr, stmt)
            }
            is RellParser.ForStmtAltContext -> {
                val kwTok = ctx.start
                val decl = toVarDeclarator(ctx.varDeclarator())
                val expr = toExpression(ctx.expression())
                val stmt = toStatement(ctx.statement())
                // headerEndPos is the ')' position (close of the for-header).
                val rpar = ctx.children.first { it is TerminalNode && it.text == ")" } as TerminalNode
                S_ForStatement(kwTok.toPos(), decl, expr, stmt, rpar.symbol.toPos())
            }
            is RellParser.BreakStmtAltContext -> {
                val start = ctx.start
                val end = ctx.stop
                S_BreakStatement(start.toPos(), end.toPos())
            }
            is RellParser.ContinueStmtAltContext -> {
                val start = ctx.start
                val end = ctx.stop
                S_ContinueStatement(start.toPos(), end.toPos())
            }
            is RellParser.UpdateStmtAltContext -> toUpdateStatement(ctx)
            is RellParser.DeleteStmtAltContext -> {
                val kwTok = ctx.start
                val target = toUpdateTarget(ctx.updateTarget())
                val end = ctx.stop
                S_DeleteStatement(kwTok.toPos(), end.toPos(), target)
            }
            is RellParser.GuardStmtAltContext -> {
                val kwTok = ctx.start
                val block = toBlockStatement(ctx.blockStmt())
                S_GuardStatement(kwTok.toPos(), block)
            }
            is RellParser.IncrementStmtAltContext -> {
                val opTok = ctx.start
                val baseExpr = toBaseExpr(ctx.baseExpr())
                val end = ctx.stop
                val opPos = opTok.toPos()
                val inc = opTok.text == "++"
                val sOp = S_PosValue<S_UnaryOp>(opPos, S_UnaryOp_IncDec(inc, false))
                val sExpr = S_UnaryExpr(opPos, sOp, baseExpr)
                S_ExprStatement(sExpr, end.toPos())
            }
            is RellParser.ExprStmtAltContext -> toExprStmt(ctx)
            else -> error("unknown statement: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toBlockStatement(ctx: RellParser.BlockStmtContext): S_BlockStatement = withCtx(ctx) {
        val lcurl = ctx.start
        val rcurl = ctx.stop
        val stmts = ctx.statement().map { toStatement(it) }.toImmList()
        return S_BlockStatement(S_PosRange(lcurl.toPos(), rcurl.toPos()), stmts)
    }

    private fun toVarStatement(ctx: RellParser.VarStmtAltContext): S_VarStatement = withCtx(ctx) {
        val kwTok = ctx.start
        val mutable = kwTok.text == "var"
        val decl = toVarDeclarator(ctx.varDeclarator())
        val expr = ctx.expression()?.let { toExpression(it) }
        val end = ctx.stop
        return S_VarStatement(kwTok.toPos(), end.toPos(), decl, expr, mutable, docCommentForToken(kwTok))
    }

    private fun toVarDeclarator(ctx: RellParser.VarDeclaratorContext): S_VarDeclarator = withCtx(ctx) {
        return when (ctx) {
            is RellParser.SimpleVarDeclaratorContext -> S_SimpleVarDeclarator(toAttrHeader(ctx.attrHeader()))
            is RellParser.TupleVarDeclaratorContext -> {
                val pos = ctx.start.toPos()
                val subs = ctx.varDeclarator().map { toVarDeclarator(it) }.toImmList()
                S_TupleVarDeclarator(pos, subs)
            }
            else -> error("unknown varDeclarator: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toWhenCondition(ctx: RellParser.WhenConditionContext): S_WhenCondition = withCtx(ctx) {
        return when (ctx) {
            is RellParser.WhenConditionElseContext -> S_WhenConditionElse(ctx.start.toPos())
            is RellParser.WhenConditionExprContext -> {
                val exprs = ctx.binaryExpr().map { toBinaryExpr(it) }.toImmList()
                S_WhenConditionExpr(exprs)
            }
            else -> error("unknown whenCondition: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toExprStmt(ctx: RellParser.ExprStmtAltContext): S_Statement = withCtx(ctx) {
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

    private fun toUpdateStatement(ctx: RellParser.UpdateStmtAltContext): S_UpdateStatement = withCtx(ctx) {
        val kwTok = ctx.start
        val target = toUpdateTarget(ctx.updateTarget())
        // updateWhat: list of `(('.'? RULE_ID ('=' | '+=' | ...))? expression)`
        // We need to walk children of the update statement: after `'(' ... ')'` the inner is what.
        val whatItems = parseUpdateWhat(ctx)
        val end = ctx.stop
        return S_UpdateStatement(kwTok.toPos(), end.toPos(), target, whatItems.toImmList())
    }

    private fun parseUpdateWhat(ctx: RellParser.UpdateStmtAltContext): List<S_UpdateWhat> = withCtx(ctx) {
        // Walk children, find the open '(' that starts the update-what list (the one AFTER the
        // updateTarget context). The structure is:
        //   'update' updateTarget '(' (whatItem (',' whatItem)* ','?) ')' ';'
        val children = ctx.children
        // Find last '(' before any ')' close at top level — the one after updateTarget.
        // Easier: find index of updateTarget context, then the next child is '('.
        val updateTargetIdx = children.indexOfFirst { it is RellParser.UpdateTargetContext }
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
            // cur is an ExpressionContext, optionally preceded by `'.'? RULE_ID assignOp`
            // (the '.' prefix is not represented in the AST).
            val nameTok = precedingNameToken(children, i) { it in ASSIGN_OP_TEXTS }
            val expr = toExpression(cur as RellParser.ExpressionContext)
            val item = if (nameTok == null) {
                S_UpdateWhat(expr.startPos, null, null, expr)
            } else {
                val sName = idTokenToName(nameTok)
                val opTok = children[i - 1] as TerminalNode
                S_UpdateWhat(sName.pos, sName, assignOpCode(opTok.text), expr)
            }
            items.add(item)
            i++
        }
        return items
    }

    private fun toUpdateTarget(ctx: RellParser.UpdateTargetContext): S_UpdateTarget = withCtx(ctx) {
        return when (ctx) {
            is RellParser.UpdateTargetAtContext -> {
                val cardinality = toAtCardinality(ctx.atExprAt())
                val where = toAtExprWhere(ctx.atExprWhere())
                val from = parseUpdateFromList(ctx)
                S_UpdateTarget_Simple(cardinality.value, from, where)
            }
            is RellParser.UpdateTargetExprContext -> {
                val head = toBaseExprHead(ctx.baseExprHead())
                val tails = ctx.baseExprTailNoCallNoAt().map { toTailNoCallNoAt(it) }
                val expr = applyTails(head, tails)
                S_UpdateTarget_Expr(expr)
            }
            else -> error("unknown updateTarget: ${ctx.javaClass.simpleName}")
        }
    }

    private fun parseUpdateFromList(ctx: RellParser.UpdateTargetAtContext): ImmList<S_UpdateFromItem> = withCtx(ctx) {
        val qNames = ctx.qualifiedName()
        // Two shapes:
        //  - single qualifiedName (no parens)        -> single item with no alias
        //  - '(' ((RULE_ID ':')? qualifiedName)+ ')' -> multi items, each may have alias
        val firstChild = ctx.children.first()
        if (firstChild is RellParser.QualifiedNameContext) {
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
            val aliasTok = precedingNameToken(ctx.children, idxInChildren) { it == ":" }
            val alias = aliasTok?.let { idTokenToName(it) }
            // Match grammar.kt: comment only when alias is present.
            val itemComment = aliasTok?.let { docCommentForToken(it.symbol) }
            items.add(S_UpdateFromItem(alias, toQualifiedName(qnCtx), itemComment))
        }
        return items.toImmList()
    }

    // ---------------------------------------------------------------------------------------------
    // Expressions

    fun toExpression(ctx: RellParser.ExpressionContext): S_Expr {
        ctx.lambdaExpr()?.let { return toLambdaExpr(it) }
        return toBinaryExpr(ctx.binaryExpr())
    }

    private fun toLambdaExpr(ctx: RellParser.LambdaExprContext): S_Expr = withCtx(ctx) {
        val lp = ctx.lambdaParams()
        val bare = lp.RULE_ID()
        val params = if (bare != null) {
            // Bare single parameter: never type-annotated.
            immListOf(S_LambdaParam(idTokenToName(bare), null))
        } else {
            // Parenthesised parameters, each with an optional explicit type.
            lp.lambdaParam().map { S_LambdaParam(idTokenToName(it.RULE_ID()), it.type()?.let { t -> toType(t) }) }.toImmList()
        }
        val body = toLambdaBody(ctx.lambdaBody())
        // Collect every simple identifier textually present in the body; the compiler intersects
        // this with the enclosing scope's initialized locals to decide what to capture by value.
        val bodyNames = mutableSetOf<String>()
        collectRefNames(ctx.lambdaBody(), bodyNames)
        return@withCtx S_LambdaExpr(ctx.start.toPos(), params, body, bodyNames)
    }

    /**
     * Gathers the text of every `RULE_ID` in a parse subtree, except identifiers used as member
     * names (those immediately preceded by `.` or `?.`), which can never denote a captured local.
     * Over-collection (e.g. named-argument or type names) is harmless: it only widens the candidate
     * set, and the compiler keeps only those that resolve to initialized outer locals.
     */
    private fun collectRefNames(ctx: ParserRuleContext, sink: MutableSet<String>) {
        val children = ctx.children ?: return
        for ((i, ch) in children.withIndex()) {
            when (ch) {
                is TerminalNode -> {
                    if (ch.symbol.type == RellParser.RULE_ID) {
                        val prevText = if (i > 0) (children[i - 1] as? TerminalNode)?.text else null
                        if (prevText != "." && prevText != "?.") sink.add(ch.text)
                    }
                }
                is ParserRuleContext -> collectRefNames(ch, sink)
            }
        }
    }

    private fun toLambdaBody(ctx: RellParser.LambdaBodyContext): S_LambdaBody = withCtx(ctx) {
        when (ctx) {
            is RellParser.LambdaBodyExprContext -> S_LambdaBody_Expr(toExpression(ctx.expression()))
            is RellParser.LambdaBodyBlockContext -> toValueBlock(ctx.valueBlock())
            else -> error("unknown lambdaBody: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toValueBlock(ctx: RellParser.ValueBlockContext): S_LambdaBody_Block = withCtx(ctx) {
        val stmts = ctx.statement().map { toStatement(it) }.toImmList()
        val result = ctx.expression()?.let { toExpression(it) }
        S_LambdaBody_Block(S_PosRange(ctx.start.toPos(), ctx.stop.toPos()), stmts, result)
    }

    private fun toValueBlockExpr(ctx: RellParser.ValueBlockContext): S_Expr = withCtx(ctx) {
        return@withCtx S_ValueBlockExpr(toValueBlock(ctx))
    }

    private fun toExprOrValueBlock(ctx: RellParser.ExprOrValueBlockContext): S_Expr = withCtx(ctx) {
        val block = ctx.valueBlock()
        return@withCtx if (block != null) toValueBlockExpr(block) else toExpression(ctx.expression())
    }

    private fun toJumpExpr(ctx: RellParser.JumpExprContext): S_Expr = withCtx(ctx) {
        when (ctx) {
            is RellParser.ReturnExprContext -> S_ReturnExpr(ctx.start.toPos(), ctx.expression()?.let { toExpression(it) })
            is RellParser.BreakExprContext -> S_BreakExpr(ctx.start.toPos())
            is RellParser.ContinueExprContext -> S_ContinueExpr(ctx.start.toPos())
            else -> error("unknown jumpExpr: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toBinaryExpr(ctx: RellParser.BinaryExprContext): S_Expr = withCtx(ctx) {
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
                operands.add(placeholderNameExpr(pos))
                break
            }
            val opCtx = children[i]
            i++
            var opExpr: S_Expr = when (opCtx) {
                is RellParser.IfExprContext -> toIfExpr(opCtx)
                is RellParser.WhenExprContext -> toWhenExpr(opCtx)
                is RellParser.JumpExprContext -> toJumpExpr(opCtx)
                is RellParser.BaseExprContext -> toBaseExpr(opCtx)
                else -> {
                    // ErrorNode / unexpected TerminalNode: synthesize a placeholder name expr at
                    // the offending position so the surrounding expression remains well-formed.
                    val pos = (opCtx as? TerminalNode)?.symbol?.toPos()
                        ?: (opCtx as? ParserRuleContext)?.start?.toPos()
                        ?: ctx.start?.toPos()
                        ?: errorPos()
                    placeholderNameExpr(pos)
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

    private fun toIfExpr(ctx: RellParser.IfExprContext): S_Expr = withCtx(ctx) {
        val kwTok = ctx.start
        val cond = toExpression(ctx.expression())
        val arms = ctx.exprOrValueBlock()
        return S_IfExpr(kwTok.toPos(), cond, toExprOrValueBlock(arms[0]), toExprOrValueBlock(arms[1]))
    }

    private fun toWhenExpr(ctx: RellParser.WhenExprContext): S_Expr = withCtx(ctx) {
        val kwTok = ctx.start
        val subject = ctx.expression()?.let { toExpression(it) }
        val cases = mutableListOf<S_WhenExprCase>()
        for (caseCtx in ctx.whenExprCase()) {
            toWhenExprCase(caseCtx.whenCondition(), caseCtx.valueBlock(), caseCtx.expression())?.let { cases.add(it) }
        }
        ctx.whenExprLastCase()?.let { lastCtx ->
            toWhenExprCase(lastCtx.whenCondition(), lastCtx.valueBlock(), lastCtx.expression())?.let { cases.add(it) }
        }
        return S_WhenExpr(kwTok.toPos(), subject, cases.toImmList())
    }

    private fun toWhenExprCase(
        condCtx: RellParser.WhenConditionContext?,
        blockCtx: RellParser.ValueBlockContext?,
        exprCtx: RellParser.ExpressionContext?,
    ): S_WhenExprCase? {
        // Under ANTLR error recovery the condition or the arm may be missing; drop the incomplete
        // case so the visitor keeps building a partial AST (see toBinaryExpr).
        condCtx ?: return null
        val arm = when {
            blockCtx != null -> toValueBlockExpr(blockCtx)
            exprCtx != null -> toExpression(exprCtx)
            else -> return null
        }
        return S_WhenExprCase(toWhenCondition(condCtx), arm)
    }

    private fun toBaseExpr(ctx: RellParser.BaseExprContext): S_Expr = withCtx(ctx) {
        var expr: S_Expr = toBaseExprHead(ctx.baseExprHead())
        // Walk children of baseExpr after baseExprHead and apply tails in source order.
        var seenHead = false
        var i = 0
        while (i < ctx.children.size) {
            val ch = ctx.children[i]
            if (!seenHead) {
                if (ch is RellParser.BaseExprHeadContext) seenHead = true
                i++
                continue
            }
            when (ch) {
                is RellParser.BaseExprTailNoCallNoAtContext -> {
                    expr = applyTail(expr, toTailNoCallNoAt(ch))
                    i++
                }
                is RellParser.CallArgsContext -> {
                    val args = toCallArgs(ch)
                    expr = S_CallExpr(expr, args)
                    i++
                }
                is RellParser.AtExprAtContext -> {
                    // Sequence: atExprAt atExprWhere atExprWhat? atExprModifiers?
                    val cardinality = toAtCardinality(ch)
                    i++
                    val whereCtx = ctx.children[i] as RellParser.AtExprWhereContext
                    val where = toAtExprWhere(whereCtx)
                    i++
                    var what: S_AtExprWhat = S_AtExprWhat_Default()
                    var limit: S_Expr? = null
                    var offset: S_Expr? = null
                    if (i < ctx.children.size && ctx.children[i] is RellParser.AtExprWhatContext) {
                        what = toAtExprWhat(ctx.children[i] as RellParser.AtExprWhatContext)
                        i++
                    }
                    if (i < ctx.children.size && ctx.children[i] is RellParser.AtExprModifiersContext) {
                        val mods = toAtExprModifiers(ctx.children[i] as RellParser.AtExprModifiersContext)
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

    private fun toBaseExprHead(ctx: RellParser.BaseExprHeadContext): S_Expr = withCtx(ctx) {
        return when (ctx) {
            is RellParser.AtExprContext -> {
                val fromItems = parseAtExprFromItems(ctx)
                val cardinality = toAtCardinality(ctx.atExprAt())
                val where = toAtExprWhere(ctx.atExprWhere())
                val what = ctx.atExprWhat()?.let { toAtExprWhat(it) } ?: S_AtExprWhat_Default()
                val mods = ctx.atExprModifiers()?.let { toAtExprModifiers(it) }
                val from = S_AtExprFrom_Complex(ctx.start.toPos(), fromItems.toImmList())
                S_AtExpr(from, cardinality, where, what, mods?.first, mods?.second)
            }
            is RellParser.NameExprContext -> S_NameExpr(toQualifiedName(ctx.qualifiedName()))
            is RellParser.DollarExprContext -> S_DollarExpr(ctx.start.toPos())
            is RellParser.AttrExprContext -> S_AttrExpr(ctx.start.toPos(), idTokenToName(ctx.RULE_ID()))
            is RellParser.IntExprContext -> integerLiteral(ctx.RULE_NUMBER())
            is RellParser.BigIntExprContext -> bigIntegerLiteral(ctx.RULE_BIG_INTEGER())
            is RellParser.DecimalExprContext -> decimalLiteral(ctx.RULE_DECIMAL())
            is RellParser.StringExprContext -> stringLiteral(ctx.RULE_STRING())
            is RellParser.BytesExprContext -> byteArrayLiteral(ctx.children.first { it is TerminalNode } as TerminalNode)
            is RellParser.TrueExprContext -> S_BooleanLiteralExpr(ctx.start.toPos(), true)
            is RellParser.FalseExprContext -> S_BooleanLiteralExpr(ctx.start.toPos(), false)
            is RellParser.NullExprContext -> S_NullLiteralExpr(ctx.start.toPos())
            is RellParser.TupleHeadContext -> toTupleHead(ctx)
            is RellParser.CreateExprContext -> toCreateExpr(ctx)
            is RellParser.MirrorStructExprContext -> {
                val mutable = ctx.children.any { it is TerminalNode && it.text == "mutable" }
                S_MirrorStructExpr(ctx.start.toPos(), mutable, toType(ctx.type()))
            }
            is RellParser.VirtualTypeExprContext -> {
                val virtType = S_VirtualType(ctx.start.toPos(), toType(ctx.type()))
                S_SpecialTypeExpr(virtType)
            }
            is RellParser.GenericTypeExprContext -> toGenericTypeExpr(ctx)
            is RellParser.EmptyMapLiteralExprContext -> {
                S_MapLiteralExpr(ctx.start.toPos(), immListOf())
            }
            is RellParser.NonEmptyMapLiteralExprContext -> {
                val exprs = ctx.expression().map { toExpression(it) }
                require(exprs.size % 2 == 0)
                val entries = (exprs.indices step 2).map { idx -> Pair(exprs[idx], exprs[idx + 1]) }.toImmList()
                S_MapLiteralExpr(ctx.start.toPos(), entries)
            }
            is RellParser.ListLiteralExprContext -> {
                val exprs = ctx.expression().map { toExpression(it) }.toImmList()
                S_ListLiteralExpr(ctx.start.toPos(), exprs)
            }
            else -> error("unknown baseExprHead: ${ctx.javaClass.simpleName}")
        }
    }

    private fun toTupleHead(ctx: RellParser.TupleHeadContext): S_Expr = withCtx(ctx) {
        // '(' (RULE_ID '=')? expression (',' (RULE_ID '=')? expression)* ','? ')'
        val fields = parseTupleFields(ctx, "=") { toExpression(it as RellParser.ExpressionContext) }
        val singleField = fields.singleField
        return if (singleField != null) S_ParenthesesExpr(ctx.start.toPos(), singleField)
        else S_TupleExpr(ctx.start.toPos(), fields.list)
    }

    private fun toCreateExpr(ctx: RellParser.CreateExprContext): S_Expr = withCtx(ctx) {
        val kwTok = ctx.start
        val qName = toQualifiedName(ctx.qualifiedName())
        // The argument list's '(' is the first one after the qualifiedName.
        val children = ctx.children
        var lparIdx = children.indexOfFirst { it is RellParser.QualifiedNameContext } + 1
        while (lparIdx < children.size && (children[lparIdx] as? TerminalNode)?.text != "(") lparIdx++
        val args = parseCallArguments(children, lparIdx)
        return S_CreateExpr(kwTok.toPos(), qName, args.list, args.posRange)
    }

    private fun toGenericTypeExpr(ctx: RellParser.GenericTypeExprContext): S_Expr = withCtx(ctx) {
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

    private fun toTailNoCallNoAt(ctx: RellParser.BaseExprTailNoCallNoAtContext): TailDescriptor = withCtx(ctx) {
        return when (ctx) {
            is RellParser.BaseExprTailMemberContext -> TailDescriptor.Member(idTokenToName(ctx.RULE_ID()))
            is RellParser.BaseExprTailSafeMemberContext -> TailDescriptor.SafeMember(idTokenToName(ctx.RULE_ID()))
            is RellParser.BaseExprTailSubscriptContext -> {
                val lbrack = ctx.start
                TailDescriptor.Subscript(lbrack.toPos(), toExpression(ctx.expression()))
            }
            is RellParser.BaseExprTailNotNullContext -> TailDescriptor.NotNull(ctx.start.toPos())
            is RellParser.BaseExprTailUnaryPostfixOpContext -> {
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

    private fun toCallArgs(ctx: RellParser.CallArgsContext): S_CallArguments = withCtx(ctx) {
        // callArgs: '(' (((RULE_ID '=')? ('*' | expression)) (',' ...)* ','?)? ')'
        return parseCallArguments(ctx.children, 0)
    }

    /**
     * Parses a parenthesised, comma-separated argument list `('.'? RULE_ID '=')? ('*' | expression)`
     * whose `'('` sits at [lparIdx]. Shared by `callArgs` and the `create(...)` argument list; only
     * the latter allows the leading `'.'`.
     */
    private fun parseCallArguments(children: List<ParseTree>, lparIdx: Int): S_CallArguments {
        val lparPos = (children[lparIdx] as TerminalNode).symbol.toPos()
        var rparPos = lparPos
        val args = mutableListOf<S_CallArgument>()
        var i = lparIdx + 1
        while (i < children.size) {
            val cur = children[i]
            if (cur is TerminalNode && cur.text == ")") { rparPos = cur.symbol.toPos(); break }
            if (cur is TerminalNode && cur.text == ",") { i++; continue }
            var j = i
            if ((children[j] as? TerminalNode)?.text == ".") j++
            var argName: S_Name? = null
            val nameTok = children.getOrNull(j) as? TerminalNode
            if (nameTok != null && nameTok.symbol.type == RellParser.RULE_ID
                && (children.getOrNull(j + 1) as? TerminalNode)?.text == "="
            ) {
                argName = idTokenToName(nameTok)
                j += 2
            }
            require(j < children.size) { "call arg: missing value" }
            val argValue: S_CallArgumentValue = when (val valChild = children[j]) {
                is TerminalNode -> {
                    require(valChild.text == "*") { "call arg: unexpected terminal ${valChild.text}" }
                    S_CallArgumentValue_Wildcard(valChild.symbol.toPos())
                }
                is RellParser.ExpressionContext -> S_CallArgumentValue_Expr(toExpression(valChild))
                else -> error("call arg: unexpected ${valChild.javaClass.simpleName}")
            }
            args.add(S_CallArgument(argName, argValue))
            i = j + 1
        }
        return S_CallArguments(args.toImmList(), S_PosRange(lparPos, rparPos))
    }

    private fun toAtCardinality(ctx: RellParser.AtExprAtContext): S_PosValue<AtCardinality> = withCtx(ctx) {
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

    private fun toAtExprWhere(ctx: RellParser.AtExprWhereContext): S_AtExprWhere = withCtx(ctx) {
        val lcurl = ctx.start
        val rcurl = ctx.stop
        val exprs = ctx.expression().map { toExpression(it) }.toImmList()
        return S_AtExprWhere(exprs, S_PosRange(lcurl.toPos(), rcurl.toPos()))
    }

    private fun toAtExprWhat(ctx: RellParser.AtExprWhatContext): S_AtExprWhat = withCtx(ctx) {
        return when (ctx) {
            is RellParser.AtExprWhatSimpleContext -> {
                val ids = ctx.RULE_ID()
                val path = ids.map { idTokenToName(it) }.toImmList()
                val dotTok = ctx.start
                S_AtExprWhat_Simple(dotTok.toPos(), path)
            }
            is RellParser.AtExprWhatComplexContext -> {
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
        ctx: RellParser.AtExprWhatComplexContext,
    ): List<S_AtExprWhatComplexField> {
        return parseAnnotatedItems(ctx, "=").map {
            S_AtExprWhatComplexField(it.name, it.expr, it.modifiers, null, it.comment)
        }
    }

    private fun toAtExprModifiers(ctx: RellParser.AtExprModifiersContext): Pair<S_Expr?, S_Expr?> = withCtx(ctx) {
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
                        limit = toExpression(children[i + 1] as RellParser.ExpressionContext)
                        i += 2
                    }
                    "offset" -> {
                        offset = toExpression(children[i + 1] as RellParser.ExpressionContext)
                        i += 2
                    }
                    else -> i++
                }
            } else i++
        }
        return Pair(limit, offset)
    }

    private fun parseAtExprFromItems(ctx: RellParser.AtExprContext): List<S_AtExprFromItem> = withCtx(ctx) {
        val items = parseAnnotatedItems(ctx, ":").map { S_AtExprFromItem(it.modifiers, it.name, it.expr, it.comment) }
        if (items.isNotEmpty()) return items
        // Error-recovery path: ANTLR may produce an `AtExprContext` whose `(...)` parens are
        // empty (or contain only synthesized error tokens). Synthesize a placeholder item so
        // `parseWithErrors` can return a usable partial AST instead of throwing.
        val pos = ctx.start?.toPos() ?: errorPos()
        return immListOf(S_AtExprFromItem(S_Modifiers(), null, placeholderNameExpr(pos), null))
    }

    /** One `annotation* (RULE_ID <sep>)? expression` item parsed by [parseAnnotatedItems]. */
    private class AnnotatedItem(
        val modifiers: S_Modifiers,
        val name: S_Name?,
        val expr: S_Expr,
        val comment: S_Comment?,
    )

    /**
     * Walks a parenthesised comma-separated list of `annotation* (RULE_ID nameSep)? expression`
     * items: at-expression `from` items (`nameSep` = ":") and complex `what` fields (`nameSep` = "=").
     * In the `from` case the list is followed by `atExprAt atExprWhere atExprWhat? atExprModifiers?`,
     * which the walk stops before.
     */
    private fun parseAnnotatedItems(ctx: ParserRuleContext, nameSep: String): List<AnnotatedItem> {
        val children = ctx.children
        val items = mutableListOf<AnnotatedItem>()
        var i = 1 // skip '('
        while (i < children.size) {
            val cur = children[i]
            // Skip separators / closing paren. Other terminals (the item's name) fall through.
            if (cur is TerminalNode) {
                if (cur.text == ")") break
                if (cur.text == ",") { i++; continue }
            }
            // Past the at-expr's `)`: subsequent children belong to the at-tail — bail.
            if (cur is RellParser.AtExprAtContext) break
            val anns = mutableListOf<S_Annotation>()
            var j = i
            while (j < children.size && children[j] is RellParser.AnnotationContext) {
                anns.add(toAnnotation(children[j] as RellParser.AnnotationContext))
                j++
            }
            var name: S_Name? = null
            var nameTokIdx = -1
            val nameTok = children.getOrNull(j) as? TerminalNode
            if (nameTok != null && nameTok.symbol.type == RellParser.RULE_ID
                && (children.getOrNull(j + 1) as? TerminalNode)?.text == nameSep
            ) {
                nameTokIdx = j
                name = idTokenToName(nameTok)
                j += 2
            }
            val expr = toExpression(children[j] as RellParser.ExpressionContext)
            val mods = if (anns.isEmpty()) S_Modifiers() else S_Modifiers(anns.toImmList())
            // Match grammar.kt: comment from the first annotation or the name token (none if neither).
            val firstTok: Token? = when {
                anns.isNotEmpty() -> (children[i] as RellParser.AnnotationContext).start
                nameTokIdx >= 0 -> (children[nameTokIdx] as TerminalNode).symbol
                else -> null
            }
            items.add(AnnotatedItem(mods, name, expr, firstTok?.let { docCommentForToken(it) }))
            i = j + 1
        }
        return items
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers

    private fun toQualifiedName(ctx: RellParser.QualifiedNameContext): S_QualifiedName = withCtx(ctx) {
        val ids = ctx.RULE_ID()
        if (ids.isEmpty()) {
            // Error-recovery: empty qualifiedName context (parser inserted a placeholder).
            // Synthesize a single placeholder S_Name so downstream code has something to
            // anchor on instead of crashing on an empty parts list.
            val pos = ctx.start?.toPos() ?: errorPos()
            return placeholderName(pos)
        }
        val parts = ids.map { idTokenToName(it) }.toImmList()
        return S_QualifiedName(parts)
    }

    /** Null-tolerant variant: when error recovery omits the qualifiedName entirely. */
    private fun toQualifiedNameOrPlaceholder(
        ctx: RellParser.QualifiedNameContext?,
        anchor: ParserRuleContext,
    ): S_QualifiedName {
        if (ctx != null) return toQualifiedName(ctx)
        val pos = anchor.start?.toPos() ?: errorPos()
        return placeholderName(pos)
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

    /** Fields of a parenthesised tuple-like list, as produced by [parseTupleFields]. */
    private class TupleFields<T>(val list: ImmList<S_GenericTupleAttr<T>>, trailingComma: Boolean) {
        /** The sole unnamed field of a list without a trailing comma — parentheses rather than a tuple. */
        val singleField: T? = list.singleOrNull()?.takeIf { !trailingComma && it.name == null }?.value
    }

    /**
     * Walks the children of a parenthesised comma-separated list of `(RULE_ID nameSep)? element`
     * items, in declaration order. Shared by tuple types (`nameSep` = ":", elements are types) and
     * tuple expressions (`nameSep` = "=", elements are expressions).
     */
    private fun <T> parseTupleFields(
        ctx: ParserRuleContext,
        nameSep: String,
        convert: (ParserRuleContext) -> T,
    ): TupleFields<T> {
        val children = ctx.children
        val fields = mutableListOf<S_GenericTupleAttr<T>>()
        var trailingComma = false
        var i = 1 // skip '('
        while (i < children.size) {
            val cur = children[i]
            if (cur is TerminalNode) {
                if (cur.text == ")") break
                if (cur.text == ",") {
                    trailingComma = (children.getOrNull(i + 1) as? TerminalNode)?.text == ")"
                }
                i++
                continue
            }
            val nameTok = precedingNameToken(children, i) { it == nameSep }
            val fieldName = nameTok?.let { idTokenToName(it) }
            // Match grammar.kt: doc-comment attaches only when the field has a name.
            val fieldComment = nameTok?.let { docCommentForToken(it.symbol) }
            fields.add(S_GenericTupleAttr(fieldName, convert(cur as ParserRuleContext), fieldComment))
            i++
        }
        return TupleFields(fields.toImmList(), trailingComma)
    }

    /**
     * The `RULE_ID` of a `RULE_ID <separator>` prefix immediately preceding `children[idx]`, or null
     * when the item at [idx] is unnamed. [sepMatches] tests the separator's text.
     */
    private fun precedingNameToken(children: List<ParseTree>, idx: Int, sepMatches: (String) -> Boolean): TerminalNode? {
        if (idx < 2) return null
        val nameTok = children[idx - 2] as? TerminalNode ?: return null
        val sepTok = children[idx - 1] as? TerminalNode ?: return null
        if (nameTok.symbol.type != RellParser.RULE_ID || !sepMatches(sepTok.text)) return null
        return nameTok
    }

    private fun integerLiteral(tk: TerminalNode): S_LiteralExpr {
        val pos = tk.symbol.toPos()
        return S_IntegerLiteralExpr(pos, RellTokenizer.decodeInteger(pos, tk.text))
    }

    private fun bigIntegerLiteral(tk: TerminalNode): S_LiteralExpr {
        val pos = tk.symbol.toPos()
        return S_CommonLiteralExpr(pos, RellTokenizer.decodeBigInteger(pos, tk.text))
    }

    private fun decimalLiteral(tk: TerminalNode): S_LiteralExpr {
        val pos = tk.symbol.toPos()
        return S_CommonLiteralExpr(pos, RellTokenizer.decodeDecimal(pos, tk.text))
    }

    private fun stringLiteral(tk: TerminalNode): S_LiteralExpr {
        val pos = tk.symbol.toPos()
        return S_StringLiteralExpr(pos, decodeStringTokenText(pos, tk.text))
    }

    private fun byteArrayLiteral(tk: TerminalNode): S_LiteralExpr {
        val pos = tk.symbol.toPos()
        return S_ByteArrayLiteralExpr(pos, RellTokenizer.decodeByteArray(pos, decodeBytesTokenText(tk.text)))
    }

    /** Single-part qualified name used wherever ANTLR error recovery dropped a real name. */
    private fun placeholderName(pos: S_Pos): S_QualifiedName = S_QualifiedName(immListOf(S_Name(pos, PLACEHOLDER_NAME)))

    private fun placeholderNameExpr(pos: S_Pos): S_Expr = S_NameExpr(placeholderName(pos))

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
