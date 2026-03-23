/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.parser.RellParser.RuleX_tkCOMMAContext
import org.antlr.v4.runtime.ParserRuleContext

class WhitespaceFormatter(private val tokenAnalyzer: TokenAnalyzer) {

    fun formatSemicolon(node: ParserRuleContext, doc: FormattableDocument) {
        val semiColon = tokenAnalyzer.tokenFor(node, ";")
        if (semiColon != null) {
            doc.prepend(semiColon) {
                it.noSpace()
                it.highPriority()
            }
        }
    }

    fun formatEqualSign(node: ParserRuleContext, doc: FormattableDocument) {
        val equalSign = tokenAnalyzer.tokenFor(node, "=")
        if (equalSign != null) {
            doc.surround(equalSign) {
                it.oneSpace()
                it.highPriority()
            }
        }
    }

    fun formatModifier(node: ParserRuleContext?, doc: FormattableDocument) {
        doc.append(node) {
            it.setNewLines(0)
            it.oneSpace()
            it.highPriority()
        }
    }

    fun formatTrailingComma(
        trailingComma: RuleX_tkCOMMAContext?,
        doc: FormattableDocument,
        newLine: Boolean = false
    ) {
        doc.prepend(trailingComma) {
            it.noSpace()
            it.setNewLines(0)
            it.superHighPriority()
        }
        if (newLine) {
            doc.append(trailingComma) { it.newLine() }
        }
    }

    fun formatType(node: ParserRuleContext, doc: FormattableDocument) {
        val paramTypeDef = tokenAnalyzer.tokenFor(node, ":")
        doc.prepend(paramTypeDef) { p -> p.noSpace() }
        doc.append(paramTypeDef) { p -> p.oneSpace() }
    }
}