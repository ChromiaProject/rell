/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.*
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Find the trailing comma of a comma-separated list inside [node]: a `,` terminal that is
 * immediately followed by the closing brace/bracket of the list. Returns `null` if no such
 * trailing comma exists. Searches only direct children, not nested contexts.
 *
 * The closing brace text is one of `)`, `]`, `}`, `>`. We don't take it as a parameter and
 * instead scan from the end for the last `,` whose next non-empty sibling is a closing token.
 */
internal fun ParserRuleContext.findTrailingComma(closing: String): TerminalNode? {
    val n = childCount
    var seenClosing = false
    for (i in n - 1 downTo 0) {
        val c = getChild(i)
        if (c is TerminalNode) {
            val t = c.symbol.text
            if (t == closing) {
                seenClosing = true
                continue
            }
            if (seenClosing) {
                return if (t == ",") c else null
            }
        } else if (seenClosing) {
            // A rule context between closing and a potential comma: not a trailing comma.
            return null
        }
    }
    return null
}

internal fun CallArgsContext.getCallArgsItems(): Pair<List<ExpressionContext>, TerminalNode?> {
    val exprs = expression()
    return Pair(exprs, findTrailingComma(")"))
}

internal fun AtExprWhereContext.getWhereItems(): Pair<List<ExpressionContext>, TerminalNode?> {
    return Pair(expression(), findTrailingComma("}"))
}

internal fun FormalParametersContext.getFormalParametersItems():
    Pair<List<FormalParameterContext>, TerminalNode?> {
    return Pair(formalParameter(), findTrailingComma(")"))
}

internal fun EnumDefContext.getEnumValues(): Pair<List<TerminalNode>, TerminalNode?> {
    // First RULE_ID is the enum name (matched by the grammar before '{'); subsequent
    // RULE_IDs inside the braces are the enum values. Walk children, skip until '{',
    // collect RULE_IDs, stop at '}'.
    val values = mutableListOf<TerminalNode>()
    var inside = false
    for (i in 0 until childCount) {
        val c = getChild(i)
        if (c is TerminalNode) {
            when (c.symbol.text) {
                "{" -> inside = true
                "}" -> break
                else -> if (inside && c.symbol.type == RULE_ID) values.add(c)
            }
        }
    }
    return Pair(values, findTrailingComma("}"))
}

internal fun KeyIndexClauseContext.getAttrItems():
    Pair<List<BaseAttributeDefinitionContext>, TerminalNode?> {
    return Pair(baseAttributeDefinition(), findTrailingComma(";"))
}

internal fun AnnotationArgsContext.getAnnotationArgs():
    Pair<List<AnnotationArgContext>, TerminalNode?> {
    return Pair(annotationArg(), findTrailingComma(")"))
}

internal fun NonEmptyMapLiteralExprContext.getMapEntries(): List<Pair<ExpressionContext, ExpressionContext>> {
    // expression items come in pairs separated by ':'. Walk children pairing them up.
    val exprs = expression()
    val result = mutableListOf<Pair<ExpressionContext, ExpressionContext>>()
    var i = 0
    while (i + 1 < exprs.size) {
        result.add(exprs[i] to exprs[i + 1])
        i += 2
    }
    return result
}

internal fun ListLiteralExprContext.getListItems(): Pair<List<ExpressionContext>, TerminalNode?> {
    return Pair(expression(), findTrailingComma("]"))
}

internal fun GenericOrNameTypeContext.getTypeArgs(): Pair<List<TypeContext>, TerminalNode?> {
    return Pair(type(), findTrailingComma(">"))
}

internal fun EntityAnnotationsContext.getAnnotationNames(): Pair<List<TerminalNode>, TerminalNode?> {
    val ids = mutableListOf<TerminalNode>()
    for (i in 0 until childCount) {
        val c = getChild(i)
        if (c is TerminalNode && c.symbol.type == RULE_ID) ids.add(c)
    }
    return Pair(ids, findTrailingComma(")"))
}

internal fun TupleVarDeclaratorContext.getVarDeclaratorItems():
    Pair<List<VarDeclaratorContext>, TerminalNode?> {
    return Pair(varDeclarator(), findTrailingComma(")"))
}

/**
 * Find every direct-child terminal in [parent] whose text equals [text]. Used to format
 * inline tokens like `=` (assignment labels), `,` (between list items), or keywords that
 * the grammar inlines instead of wrapping in dedicated rule contexts.
 */
internal fun ParserRuleContext.directTerminals(text: String): List<TerminalNode> {
    val result = mutableListOf<TerminalNode>()
    for (i in 0 until childCount) {
        val c = getChild(i)
        if (c is TerminalNode && c.symbol.text == text) result.add(c)
    }
    return result
}
