/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.parser.RellParser.*

class BinaryOpFormatter : NodeFormatter<RuleX_BinaryOperatorContext> {
    override fun format(node: RuleX_BinaryOperatorContext, doc: FormattableDocument) {
        doc.surround(node) {
            it.oneSpace()
            it.highPriority()
        }
    }
}

class BinOp17Formatter : NodeFormatter<RuleX_BinaryOperator_17Context> {
    override fun format(node: RuleX_BinaryOperator_17Context, doc: FormattableDocument) {
        doc.surround(node) {
            it.oneSpace()
            it.highPriority()
        }
        doc.surround(node.ruleX_tkIN()) {
            it.oneSpace()
            it.highPriority()
        }
    }
}

class AssignOpFormatter : NodeFormatter<RuleX_AssignOpContext> {
    override fun format(node: RuleX_AssignOpContext, doc: FormattableDocument) {
        doc.surround(node) {
            it.oneSpace()
            it.highPriority()
        }
    }
}

class IncrementOpFormatter : NodeFormatter<RuleX_IncrementOperatorContext> {
    override fun format(node: RuleX_IncrementOperatorContext, doc: FormattableDocument) {
        doc.append(node) { it.noSpace() }
    }
}
