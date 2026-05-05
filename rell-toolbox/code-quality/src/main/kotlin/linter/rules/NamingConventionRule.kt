/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.TerminalNameContext
import net.postchain.rell.toolbox.linter.issues.NamingConventionIssue
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Checks that defining identifiers follow snake_case (or, where allowed, SCREAMING_SNAKE_CASE).
 *
 * In the legacy grammar a synthetic `RuleX_NameNode` rule wrapped every defining identifier and
 * the rule simply visited that wrapper. The canonical `Rell.g4` grammar emits bare
 * `RULE_ID` terminals at all definition sites, so this rule overrides every relevant
 * "definition" visitor and pulls the first identifier terminal out by hand.
 */
class NamingConventionRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    companion object {
        const val RULE_ID = "rule_naming_convention"
    }

    override val ruleId = RULE_ID

    override fun visitFunctionDef(ctx: RellParser.FunctionDefContext) =
        checkFirstIdentifier(ctx, uppercaseAllowed = false)

    override fun visitQueryDef(ctx: RellParser.QueryDefContext) =
        checkFirstIdentifier(ctx, uppercaseAllowed = false)

    override fun visitOpDef(ctx: RellParser.OpDefContext) =
        checkFirstIdentifier(ctx, uppercaseAllowed = false)

    override fun visitEntityDef(ctx: RellParser.EntityDefContext) =
        checkFirstIdentifier(ctx, uppercaseAllowed = false)

    override fun visitStructDef(ctx: RellParser.StructDefContext) =
        checkFirstIdentifier(ctx, uppercaseAllowed = false)

    override fun visitObjectDef(ctx: RellParser.ObjectDefContext) =
        checkFirstIdentifier(ctx, uppercaseAllowed = false)

    override fun visitEnumDef(ctx: RellParser.EnumDefContext) {
        // The enum's own name (first RULE_ID) is a regular def name; subsequent RULE_IDs are values.
        val ids = collectRuleIdsAtTopLevel(ctx)
        if (ids.isEmpty()) return
        check(ids[0], uppercaseAllowed = false)
        // Values: SCREAMING_SNAKE allowed.
        for (i in 1 until ids.size) {
            check(ids[i], uppercaseAllowed = true)
        }
    }

    override fun visitNamespaceDef(ctx: RellParser.NamespaceDefContext) =
        checkFirstIdentifier(ctx, uppercaseAllowed = false)

    override fun visitConstantDef(ctx: RellParser.ConstantDefContext) {
        // Top-level `val NAME = ...` constants — SCREAMING_SNAKE allowed.
        val nameId = ctx.RULE_ID() ?: return
        check(nameId, uppercaseAllowed = true)
    }

    override fun visitFormalParameter(ctx: RellParser.FormalParameterContext) =
        checkFirstIdentifier(ctx, uppercaseAllowed = false)

    override fun visitNameTypeAttrHeader(ctx: RellParser.NameTypeAttrHeaderContext) {
        // Skip when this attr header is part of a simple-var-declarator: the SimpleVarDeclarator
        // visitor handles those (avoids double-reporting). Entity/struct attrs go through here.
        if (ctx.parent is RellParser.SimpleVarDeclaratorContext) return
        checkFirstIdentifier(ctx, uppercaseAllowed = isAttrUppercaseAllowed(ctx))
    }

    override fun visitAnonAttrHeader(ctx: RellParser.AnonAttrHeaderContext) {
        // Anonymous attr headers reference an existing type name in entity/struct bodies, so they
        // are not defining positions there. Skip.
    }

    override fun visitSimpleVarDeclarator(ctx: RellParser.SimpleVarDeclaratorContext) {
        // First RULE_ID under a SimpleVarDeclarator is the variable name being declared.
        checkFirstIdentifier(ctx, uppercaseAllowed = false)
    }

    private fun isAttrUppercaseAllowed(ctx: RellParser.NameTypeAttrHeaderContext): Boolean {
        // Use IDE symbol kind to permit constants and enum values.
        val terminal = firstRuleIdTerminal(ctx) ?: return false
        val token = terminal.symbol
        val symbolInterval = Interval.of(token.startIndex, token.stopIndex)
        val symbolInfo = resource.locationInfo[symbolInterval]
        val kind = symbolInfo?.ideSymbolInfo?.kind
        return kind == IdeSymbolKind.MEM_ENUM_VALUE || kind == IdeSymbolKind.DEF_CONSTANT
    }

    private fun checkFirstIdentifier(ctx: org.antlr.v4.runtime.ParserRuleContext, uppercaseAllowed: Boolean) {
        val terminal = firstRuleIdTerminal(ctx) ?: return
        check(terminal, uppercaseAllowed = uppercaseAllowed)
    }

    private fun check(terminal: TerminalNode, uppercaseAllowed: Boolean) {
        val token = terminal.symbol
        if (isDisabled(config.ruleNamingConvention) || hasIgnoreCommentOnTop(token)) {
            return
        }
        val name = token.text
        if (name == "<missing RULE_ID>") {
            return
        }
        if (!isSnakeCase(name, uppercaseAllowed)) {
            val wrapper = TerminalNameContext(terminal)
            report(NamingConventionIssue(wrapper, ruleId, "'$name' should be in snake case"))
        }
    }

    private fun isSnakeCase(name: String, uppercaseAllowed: Boolean = false): Boolean {
        return if (uppercaseAllowed) {
            name.matches(Regex("^[a-z_][a-z0-9_]*$")) || name.matches(Regex("^[A-Z_][A-Z0-9_]*$"))
        } else {
            name.matches(Regex("^[a-z_][a-z0-9_]*$"))
        }
    }

    private fun firstRuleIdTerminal(ctx: org.antlr.v4.runtime.tree.ParseTree): TerminalNode? {
        if (ctx is TerminalNode) {
            if (ctx.symbol.type == net.postchain.rell.base.compiler.parser.antlr.RellLexer.RULE_ID) {
                return ctx
            }
            return null
        }
        for (i in 0 until ctx.childCount) {
            val r = firstRuleIdTerminal(ctx.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun collectRuleIdsAtTopLevel(ctx: org.antlr.v4.runtime.ParserRuleContext): List<TerminalNode> {
        // Collect RULE_ID terminals in source order, recursing into children but skipping any
        // nested rule that introduces its own naming scope (like a nested function/struct).
        val out = mutableListOf<TerminalNode>()
        collectRuleIdsRecursive(ctx, out)
        return out
    }

    private fun collectRuleIdsRecursive(node: org.antlr.v4.runtime.tree.ParseTree, out: MutableList<TerminalNode>) {
        if (node is TerminalNode) {
            if (node.symbol.type == net.postchain.rell.base.compiler.parser.antlr.RellLexer.RULE_ID) {
                out.add(node)
            }
            return
        }
        for (i in 0 until node.childCount) {
            collectRuleIdsRecursive(node.getChild(i), out)
        }
    }
}
