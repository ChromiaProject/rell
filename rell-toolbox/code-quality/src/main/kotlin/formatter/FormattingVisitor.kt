/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter

import org.antlr.v4.runtime.ParserRuleContext

class FormattingVisitor(val formatterRegistry: FormatterRegistry) {
    fun visit(node: ParserRuleContext, doc: FormattableDocument) {
        val nodeFormatter = formatterRegistry.findFormatter(node.javaClass)
        if (nodeFormatter != null) {
            nodeFormatter.format(node, doc)
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child is ParserRuleContext) {
                val nodeFormatter = formatterRegistry.findFormatter(child.javaClass)
                if (nodeFormatter != null) {
                    nodeFormatter.format(child, doc)
                } else {
                    visit(child, doc)
                }
            }
        }
    }
}