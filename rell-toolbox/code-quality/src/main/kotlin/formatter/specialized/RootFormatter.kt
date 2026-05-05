/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.*
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter

class RootNodeFormatter(
    private val expressionFormatter: ExpressionFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<FileContext> {
    override fun format(node: FileContext, doc: FormattableDocument) {
        node.moduleHeader()?.let { doc.format(it) }
        expressionFormatter.formatOpeningClosingLines(node.annotatedDef(), doc)
        node.annotatedDef().forEach { doc.format(it) }
    }
}

class MooduleHeaderFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<ModuleHeaderContext> {
    override fun format(node: ModuleHeaderContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        doc.prepend(node) { it.noSpace() }
        doc.append(node) {
            it.setNewLines(2)
            it.superHighPriority()
        }
    }
}

class ModifierFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<ModifierContext> {
    override fun format(node: ModifierContext, doc: FormattableDocument) {
        // The new grammar inlines keyword modifiers and annotations into the same alt list.
        // If the modifier is an annotation, terminate the line; if a keyword, ensure one space.
        val annotation = node.annotation()
        if (annotation != null) {
            doc.append(annotation) {
                it.newLine()
                it.highPriority()
            }
        } else {
            // Keyword modifier (abstract / mutable / override): single space afterwards.
            whitespaceFormatter.formatModifier(node, doc)
        }
    }
}
