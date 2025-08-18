package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.parser.RellParser.RuleX_FormalParameterContext
import org.antlr.v4.runtime.ParserRuleContext

class ArgumentFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer
) {

    fun formatParametersType(params: List<RuleX_FormalParameterContext>?, doc: FormattableDocument) {
        if (params.isNullOrEmpty()) {
            return
        }
        for (param in params) {
            whitespaceFormatter.formatType(param.ruleX_AttrHeader(), doc)
        }
    }

    fun formatArguments(args: List<ParserRuleContext>?, doc: FormattableDocument, indent: Boolean = true) {
        if (args.isNullOrEmpty()) {
            return
        }
        formatArguments(args, doc, null, indent)
    }

    private fun formatArguments(
        args: List<ParserRuleContext>,
        doc: FormattableDocument,
        length: Int?,
        indent: Boolean = true
    ) {
        val formatAsMultiLine = if (length != null) {
            lineAnalyzer.formatAsMultiLine(args, length)
        } else {
            lineAnalyzer.formatAsMultiLine(args)
        }
        formatArguments(args, doc, formatAsMultiLine, indent)
    }

    fun formatArguments(
        args: List<ParserRuleContext>?,
        doc: FormattableDocument,
        formatAsMultiLine: Boolean,
        indent: Boolean = true
    ) {
        if (args.isNullOrEmpty()) {
            return
        }
        args.forEachIndexed { index, arg ->
            if (formatAsMultiLine) {
                doc.prepend(arg) { p -> p.newLine() }
                if (indent) {
                    doc.prepend(arg) { p -> p.indent() }
                }
                if (index == args.lastIndex) {
                    doc.append(arg) { p -> p.newLine() }
                } else {
                    doc.append(arg) { p -> p.noSpace() }
                }
            } else {
                doc.prepend(arg) { p -> p.oneSpace() }
                doc.append(arg) { p -> p.noSpace() }
            }

            val assignToken = tokenAnalyzer.tokenFor(arg, "=")
            val equalsToken = tokenAnalyzer.tokenFor(arg, "==")
            if (assignToken != null) {
                doc.surround(assignToken) { p ->
                    p.oneSpace()
                    p.highPriority()
                }
            }
            if (equalsToken != null) {
                doc.surround(equalsToken) { p ->
                    p.oneSpace()
                    p.highPriority()
                }
            }
        }
    }
}