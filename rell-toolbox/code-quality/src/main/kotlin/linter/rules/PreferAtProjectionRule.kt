/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.PreferAtProjectionIssue
import net.postchain.rell.toolbox.linter.singleBaseExpr
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Flags the N+1 query pattern: a `for` loop over an unprojected entity at-expression whose body
 * only reads the loop variable's attributes. Every such read runs its own SQL query per row,
 * whereas projecting the attributes in the at-expression's what-clause fetches everything in one:
 *
 *     for (p in person @* {}) { use(p.name); }          // 1 + N queries
 *     for (name in person @* {} (.name)) { use(name); } // 1 query
 *
 * Recognized sources: `entity @* {...}` / `@+`, the self-projection form `entity @* {...} (entity)`,
 * a single aliased from-clause `(a: entity) @* {...}`, and a `val` holding such an at-expression
 * that is used only as the loop source. The loop body must use the variable exclusively as
 * `<var>.<attr>...` reads: any other use (update/delete target, assignment target, bare reference)
 * means the whole entity is needed, and the rule stays silent. `.rowid`-only loops are also
 * skipped — the rowid is carried in the handle and costs no extra query.
 */
internal class PreferAtProjectionRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.ForStmtAltContext::class.java,
    )

    override fun visitForStmtAlt(ctx: RellParser.ForStmtAltContext) {
        if (isDisabled(config.rulePreferAtProjection) || hasIgnoreCommentOnTop(ctx.start) || hasSemanticErrors()) {
            return
        }
        val declarator = ctx.varDeclarator() as? RellParser.SimpleVarDeclaratorContext ?: return
        val varName = firstRuleId(declarator)?.text ?: return
        if (varName == "_") return

        val sourceBase = ctx.expression()?.binaryExpr()?.singleBaseExpr() ?: return
        val at = entityAtSource(sourceBase, ctx) ?: return
        val body = ctx.statement() ?: return
        val attrs = projectableReads(body, varName) ?: return
        if (attrs.isEmpty() || attrs.all { it == ROWID }) return

        val what = attrs.distinct().joinToString(", ") { ".$it" }
        report(
            PreferAtProjectionIssue(
                at.anchor,
                ruleId,
                "Each '$varName.<attr>' in the loop body runs a separate SQL query; " +
                    "project the attributes in the at-expression instead: " +
                    "'${at.entityText} @${at.cardinality} {...} ($what)'"
            )
        )
    }

    private class EntityAtSource(val anchor: ParserRuleContext, val entityText: String, val cardinality: String)

    /**
     * Recognizes [base] as an unprojected `@*`/`@+` entity at-expression, directly or through a
     * single-use `val`. [forCtx] is the loop the source feeds, used for the `val` lookup.
     */
    private fun entityAtSource(base: RellParser.BaseExprContext, forCtx: RellParser.ForStmtAltContext): EntityAtSource? {
        directEntityAt(base)?.let { return it }
        // A bare single-segment name: try to resolve it as a `val` initialized with a qualifying
        // at-expression and used only as this loop's source.
        if (base.childCount != 1) return null
        val head = base.baseExprHead() as? RellParser.NameExprContext ?: return null
        val ids = head.qualifiedName()?.RULE_ID() ?: return null
        if (ids.size != 1) return null
        return valEntityAt(ids[0].text, forCtx)
    }

    /** The direct at-expression forms: `entity @* {...}` and `(a: entity) @* {...}`. */
    private fun directEntityAt(base: RellParser.BaseExprContext): EntityAtSource? {
        val kids = base.children ?: return null
        val head = base.baseExprHead() ?: return null

        if (head is RellParser.AtExprContext) {
            // Parenthesized from-clause: at/where/what/modifiers live inside the head; the base
            // expression must carry no further tails.
            if (kids.size != 1) return null
            return fromClauseEntityAt(head)
        }

        // Plain form: name head immediately followed by the at-operation, nothing after the
        // at-segment. `kids` is [head, atExprAt, atExprWhere, atExprWhat?, atExprModifiers?].
        if (head !is RellParser.NameExprContext) return null
        val at = kids.getOrNull(1) as? RellParser.AtExprAtContext ?: return null
        if (kids.getOrNull(2) !is RellParser.AtExprWhereContext) return null
        val card = cardinality(at) ?: return null
        var i = 3
        val what = kids.getOrNull(i) as? RellParser.AtExprWhatContext
        if (what != null) i++
        if (kids.getOrNull(i) is RellParser.AtExprModifiersContext) i++
        if (i != kids.size) return null

        val nameIds = head.qualifiedName()?.RULE_ID() ?: return null
        val entityId = nameIds.last()
        if (!isEntity(entityId)) return null
        if (!whatIsAbsentOrSelf(what, setOf(entityId.text))) return null
        return EntityAtSource(base, head.text, card)
    }

    /** A `(a: entity) @* {...}` head with exactly one, entity-typed, from-item. */
    private fun fromClauseEntityAt(head: RellParser.AtExprContext): EntityAtSource? {
        val exprs = head.expression()
        if (exprs.size != 1 || head.annotation().isNotEmpty()) return null
        val at = head.atExprAt() ?: return null
        val card = cardinality(at) ?: return null
        val itemBase = exprs[0].binaryExpr()?.singleBaseExpr() ?: return null
        if (itemBase.childCount != 1) return null
        val itemHead = itemBase.baseExprHead() as? RellParser.NameExprContext ?: return null
        val itemIds = itemHead.qualifiedName()?.RULE_ID() ?: return null
        val entityId = itemIds.last()
        if (!isEntity(entityId)) return null
        // The what-clause may project the entity itself under its name or its `a:` alias.
        val selfNames = mutableSetOf(entityId.text)
        aliasOf(head, exprs[0])?.let { selfNames.add(it) }
        if (!whatIsAbsentOrSelf(head.atExprWhat(), selfNames)) return null
        return EntityAtSource(head, itemHead.text, card)
    }

    /** The `a` of an `a: <expr>` from-item, or null if the item is unaliased. */
    private fun aliasOf(head: RellParser.AtExprContext, item: RellParser.ExpressionContext): String? {
        val kids = head.children ?: return null
        val idx = kids.indexOf(item)
        if (idx < 2) return null
        if ((kids[idx - 1] as? TerminalNode)?.text != ":") return null
        return (kids[idx - 2] as? TerminalNode)?.takeIf { it.symbol.type == RellLexer.RULE_ID }?.text
    }

    /**
     * Resolves the loop source name as a `val <name> = <entity at-expr>;` in an enclosing block,
     * used nowhere else. Any other use of the value means projecting would change its type.
     */
    private fun valEntityAt(name: String, forCtx: RellParser.ForStmtAltContext): EntityAtSource? {
        var child: RuleContext = forCtx
        var block = forCtx.parent
        while (block != null) {
            if (block is RellParser.BlockStmtContext) {
                for (stmt in block.statement()) {
                    if (stmt === child || stmt.start.startIndex >= forCtx.start.startIndex) break
                    val decl = stmt as? RellParser.VarStmtAltContext ?: continue
                    if ((decl.getChild(0) as? TerminalNode)?.text != "val") continue
                    val declarator = decl.varDeclarator() as? RellParser.SimpleVarDeclaratorContext ?: continue
                    val declId = firstRuleId(declarator) ?: continue
                    if (declId.text != name) continue
                    val init = decl.expression()?.binaryExpr()?.singleBaseExpr() ?: return null
                    val at = directEntityAt(init) ?: return null
                    if (!usedOnlyAsLoopSource(block, name, declId)) return null
                    // Anchor on the loop source so the report sits on the loop, but describe the
                    // initializer's at-expression in the message.
                    return EntityAtSource(forCtx.expression(), at.entityText, at.cardinality)
                }
            }
            child = block
            block = block.parent
        }
        return null
    }

    /** True if within [scope] the only reference to [name] besides [declId] is a single bare use. */
    private fun usedOnlyAsLoopSource(scope: ParseTree, name: String, declId: TerminalNode): Boolean {
        var uses = 0
        forEachRuleId(scope, name) { terminal ->
            if (terminal === declId) return@forEachRuleId
            val qName = terminal.parent as? RellParser.QualifiedNameContext ?: run { uses += 2; return@forEachRuleId }
            if (qName.RULE_ID().indexOf(terminal) != 0) return@forEachRuleId
            uses++
        }
        return uses == 1
    }

    /**
     * Attribute names read off [varName] in the loop [body], or null when the variable escapes:
     * a bare (whole-entity) reference, an assignment/increment target, an update target, or any
     * occurrence this rule does not positively recognize (declarations shadowing the name, named
     * arguments, member accesses spelled `.<varName>` on other values are all treated as escapes).
     */
    private fun projectableReads(body: ParseTree, varName: String): List<String>? {
        val attrs = mutableListOf<String>()
        var escaped = false
        forEachRuleId(body, varName) { terminal ->
            if (escaped) return@forEachRuleId
            val qName = terminal.parent as? RellParser.QualifiedNameContext
            if (qName == null) {
                escaped = true
                return@forEachRuleId
            }
            val ids = qName.RULE_ID()
            if (ids.indexOf(terminal) != 0) return@forEachRuleId // `other.<varName>`: not our variable
            if (ids.size < 2 || isWriteTarget(qName)) {
                escaped = true
                return@forEachRuleId
            }
            // `p.a.b` reads the attribute path `a.b`; in `p.a.b()` the last segment is a method
            // call, so only `a` is a projectable attribute — and a direct method on the entity
            // itself (`p.to_struct()`) is a whole-entity use.
            val baseExpr = qName.parent?.parent as? RellParser.BaseExprContext
            val isCall = baseExpr?.children?.getOrNull(1) is RellParser.CallArgsContext
            val path = ids.subList(1, if (isCall) ids.size - 1 else ids.size)
            if (path.isEmpty()) {
                escaped = true
                return@forEachRuleId
            }
            attrs.add(path.joinToString(".") { it.text })
        }
        return if (escaped) null else attrs
    }

    /** True if the base expression headed by [qName] is written to rather than read. */
    private fun isWriteTarget(qName: RellParser.QualifiedNameContext): Boolean {
        val baseExpr = qName.parent?.parent as? RellParser.BaseExprContext ?: return false
        return when (val stmt = baseExpr.parent) {
            // `p.attr = v;` / `p.attr += v;`: an operator between the target and `;`.
            is RellParser.ExprStmtAltContext -> stmt.getChild(0) === baseExpr && stmt.childCount > 2
            is RellParser.IncrementStmtAltContext -> true
            else -> baseExpr.parent is RellParser.UpdateTargetExprContext
        }
    }

    private fun whatIsAbsentOrSelf(what: RellParser.AtExprWhatContext?, selfNames: Set<String>): Boolean {
        if (what == null) return true
        if (what !is RellParser.AtExprWhatComplexContext) return false
        // Exactly `( name )`: one expression, no annotations, no `name =` prefix.
        return what.childCount == 3 && (what.getChild(1) as? RellParser.ExpressionContext)?.text in selfNames
    }

    private fun cardinality(at: RellParser.AtExprAtContext): String? {
        val symbol = (at.getChild(1) as? TerminalNode)?.text
        return if (symbol == "*" || symbol == "+") symbol else null
    }

    private fun isEntity(id: TerminalNode): Boolean {
        val token = id.symbol
        val info = resource.locationInfo[Interval.of(token.startIndex, token.stopIndex)]
        return info?.ideSymbolInfo?.kind == IdeSymbolKind.DEF_ENTITY
    }

    private fun firstRuleId(ctx: ParseTree): TerminalNode? {
        if (ctx is TerminalNode) {
            return ctx.takeIf { it.symbol.type == RellLexer.RULE_ID }
        }
        for (i in 0 until ctx.childCount) {
            firstRuleId(ctx.getChild(i))?.let { return it }
        }
        return null
    }

    private inline fun forEachRuleId(node: ParseTree, name: String, action: (TerminalNode) -> Unit) {
        val stack = ArrayDeque<ParseTree>()
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current is TerminalNode) {
                if (current.symbol.type == RellLexer.RULE_ID && current.text == name) action(current)
                continue
            }
            for (i in current.childCount - 1 downTo 0) {
                stack.addLast(current.getChild(i))
            }
        }
    }

    companion object {
        const val RULE_ID = "rule_prefer_at_projection"
        private const val ROWID = "rowid"
    }
}
