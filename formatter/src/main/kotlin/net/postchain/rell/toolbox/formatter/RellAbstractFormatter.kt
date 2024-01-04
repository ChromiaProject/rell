package net.postchain.rell.toolbox.formatter

import net.postchain.rell.toolbox.core.parser.RellLexer
import net.postchain.rell.toolbox.core.parser.RellParser
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

abstract class RellAbstractFormatter(
    val parser: RellParser,
    private val source: String,
    private val formatterOptions: FormatterOptions
) {

    fun formatExprTailMultiline(
        currentExpr: ParserRuleContext?,
        previousExpr: ParserRuleContext?,
        doc: FormattableDocument
    ) {
        when (currentExpr) {
            is RellParser.RuleX_BaseExprTailMemberContext -> {
                doc.prepend(currentExpr.ruleX_Name()) { p -> p.noSpace() }
                doc.append(currentExpr) { p -> p.noSpace() }
                doc.prepend(currentExpr) { p -> p.newLine() }
            }

            is RellParser.RuleX_BaseExprTailContext -> {
                if (currentExpr.ruleX_BaseExprTailCall() != null) {
                    formatExprTailCall(currentExpr, previousExpr, doc, false)
                } else {
                    if (currentExpr.ruleX_BaseExprTailMember() != null) {
                        formatExprTailMultiline(currentExpr.ruleX_BaseExprTailMember(), currentExpr, doc)
                    } else {
                        doc.format(currentExpr)
                    }
                }
            }

            else -> {
                doc.format(currentExpr)
            }
        }
    }

    fun formatExprTailCall(
        currentExpr: RellParser.RuleX_BaseExprTailContext,
        previousExpr: ParserRuleContext?,
        doc: FormattableDocument,
        indent: Boolean = true
    ) {
        if (previousExpr is RellParser.RuleX_BaseExprTailContext) {
            doc.interiorIndent(currentExpr)
        }
        formatBracePairWithoutSpace(
            currentExpr.ruleX_BaseExprTailCall().ruleX_CallArgs(),
            doc,
            BracePairTypes.PARENTHESES
        )
        formatArguments(
            currentExpr.ruleX_BaseExprTailCall().ruleX_CallArgs().ruleX_CallArg(),
            doc,
            indent = indent
        )
        doc.format(currentExpr.ruleX_BaseExprTailCall().ruleX_CallArgs())
    }


    fun formatExprTailSingleline(xBaseExprTail: ParserRuleContext, doc: FormattableDocument) {
        if (xBaseExprTail is RellParser.RuleX_BaseExprTailMemberContext) {
            doc.prepend(xBaseExprTail) { p -> p.noSpace() }
            doc.append(xBaseExprTail) { p -> p.noSpace() }
        } else if (xBaseExprTail is RellParser.RuleX_BaseExprTailCallContext) {
            val exprTailCall = xBaseExprTail as RellParser.RuleX_BaseExprTailCallContext
            doc.append(xBaseExprTail) { p -> p.noSpace() }
            formatBracePairWithoutSpace(exprTailCall.ruleX_CallArgs(), doc, BracePairTypes.PARENTHESES)
            formatArguments(exprTailCall.ruleX_CallArgs().ruleX_CallArg(), doc)
            doc.format(exprTailCall.ruleX_CallArgs())
        } else if (xBaseExprTail is RellParser.RuleX_BaseExprTailContext) {
            if (xBaseExprTail.ruleX_BaseExprTailCall() != null) {
                formatExprTailSingleline(xBaseExprTail.ruleX_BaseExprTailCall(), doc)
            }
            doc.format(xBaseExprTail)
        } else {
            doc.format(xBaseExprTail)
        }
    }

    fun formatMultiLineStmts(
        xUnaryExpr: RellParser.RuleX_UnaryExprContext,
        stmts: List<RellParser.RuleX_BinaryExprOperandContext>,
        doc: FormattableDocument
    ) {
        doc.prepend(xUnaryExpr) { p -> p.newLine() }
        doc.prepend(xUnaryExpr) { p -> p.indent() }
        doc.interiorIndent(xUnaryExpr)
        stmts.forEachIndexed { index, expr ->
            doc.surround(expr.ruleX_BinaryOperator()) { p -> p.oneSpace() }
            val isLogicalOperator = expr.ruleX_BinaryOperator().ruleX_BinaryOperator_14() != null ||
                    expr.ruleX_BinaryOperator().ruleX_BinaryOperator_13() != null
            if (isLogicalOperator) {
                doc.surround(expr.ruleX_BinaryOperator()) { p ->
                    p.newLine()
                    p.indent()
                }
            }
            if (index == stmts.lastIndex) doc.append(expr.ruleX_UnaryExpr()) { p -> p.newLine() }
        }
    }

    fun lineSeparateExpr(currentExpr: ParserRuleContext, previousExp: ParserRuleContext): Boolean {
        val previousExprLineNr = previousExp.stop.line
        val currentExprLineNr = currentExpr.stop.line
        val isLineSeparated = previousExprLineNr != currentExprLineNr

        return if (getLineLength(currentExpr) > formatterOptions.maxLineWidth || isLineSeparated) {
            true
        } else {
            false
        }
    }

    fun lineSeparateExpr(currentExpr: Token, previousExp: Token): Boolean {
        val previousExprLineNr = previousExp.line
        val currentExprLineNr = currentExpr.line
        val isLineSeperated = previousExprLineNr != currentExprLineNr
        return if (getLineLength(currentExpr) > formatterOptions.maxLineWidth || isLineSeperated) {
            true
        } else {
            false
        }
    }

    fun lineSeparateArguments(methodDef: ParserRuleContext, pair: BracePairTypes): Boolean {
        val bracketOpening = tokenFor(methodDef, pair.opening)
        val bracketClosing = tokenFor(methodDef, pair.closing)
        var isLineSeperated = false
        if (bracketOpening != null && bracketClosing != null) {
            val openingLine = bracketOpening.symbol.line
            val closingLine = bracketClosing.symbol.line
            isLineSeperated = openingLine != closingLine
        }
        return getLineLength(methodDef) > formatterOptions.maxLineWidth || isLineSeperated
    }

    fun getLineLength(node: ParserRuleContext): Int {
        val lineStartOffset = node.start.startIndex - node.start.charPositionInLine
        val lineEndOffset = source.indexOf('\n', node.start.startIndex)
        return if (lineEndOffset != -1) lineEndOffset - lineStartOffset else node.text.length
    }

    fun getLineLength(node: Token): Int {
        val lineStartOffset = node.startIndex - node.charPositionInLine
        val lineEndOffset = source.indexOf('\n', node.startIndex)
        return if (lineEndOffset != -1) lineEndOffset - lineStartOffset else node.text.length
    }

    fun formatAsMultiLine(args: List<ParserRuleContext>): Boolean {
        var lineLength = 0
        if (args.isNotEmpty()) {
            lineLength = getLineLength(args[0])
        }
        return formatAsMultiLine(args, lineLength)
    }

    fun formatAsMultiLine(args: List<ParserRuleContext>, length: Int): Boolean {
        var isMultiLine = false
        val nrOfArgs: Int = args.size
        if (nrOfArgs > 0) {
            val defStartLine = args[0].start.line
            val defEndLine = args[nrOfArgs - 1].stop.line
            isMultiLine = defStartLine != defEndLine
        }
        return length > formatterOptions.maxLineWidth || isMultiLine
    }

    fun formatParametersType(params: List<RellParser.RuleX_FormalParameterContext>, doc: FormattableDocument) {
        for (param in params) {
            formatType(param.ruleX_AttrHeader(), doc)
        }
    }

    fun formatArguments(args: List<ParserRuleContext>, doc: FormattableDocument, indent: Boolean = true) {
        formatArguments(args, doc, null, indent)
    }

    fun formatArguments(
        args: List<ParserRuleContext>,
        doc: FormattableDocument,
        length: Int?,
        indent: Boolean = true
    ) {
        val formatAsMultiLine = if (length != null) {
            formatAsMultiLine(args, length)
        } else {
            formatAsMultiLine(args)
        }
        formatArguments(args, doc, formatAsMultiLine, indent)
    }

    fun formatArguments(
        args: List<ParserRuleContext>,
        doc: FormattableDocument,
        formatAsMultiLine: Boolean,
        indent: Boolean = true
    ) {
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

            val assignToken = tokenFor(arg, "=")
            val equalsToken = tokenFor(arg, "==")
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

    fun formatType(node: ParserRuleContext, doc: FormattableDocument) {
        val paramTypeDef = tokenFor(node, ":")
        doc.prepend(paramTypeDef) { p -> p.noSpace() }
        doc.append(paramTypeDef) { p -> p.oneSpace() }
    }

    fun formatOpeningClosingLines(
        definitions: List<RellParser.RuleX_AnnotatedDefContext>,
        doc: FormattableDocument
    ) {
        if (definitions.isNotEmpty()) {
            val firstDef = definitions[0]

            val fileStartsWithComment = previousHiddenRegionList(firstDef.start).any {
                it.type == RellLexer.RULE_ML_COMMENT ||
                        it.type == RellLexer.RULE_SL_COMMENT
            }
            val newLines = if (fileStartsWithComment) 1 else 0

            doc.prepend(firstDef) {
                it.setNewLines(newLines);
                it.noSpace();
                it.highPriority();
            }

            val lastDef = definitions[definitions.size - 1]
            doc.append(lastDef) {
                it.setNewLines(1)
                it.noSpace()
                it.highPriority()
            }
        }
    }

    fun formatBracePairWithoutSpace(node: ParserRuleContext, doc: FormattableDocument, pair: BracePairTypes) {
        val openingNode = tokenFor(node, pair.opening)
        val closingNode = tokenFor(node, pair.closing)
        if (openingNode != null && closingNode != null) {
            doc.append(openingNode) { p ->
                p.noSpace()
                p.highPriority()
            }
            doc.prepend(closingNode) { p ->
                p.noSpace()
                p.highPriority()
            }
        }
    }

    fun formatBracePairWithSpace(node: ParserRuleContext, doc: FormattableDocument, pair: BracePairTypes) {
        val openingNode = tokenFor(node, pair.opening)
        val closingNode = tokenFor(node, pair.closing)
        if (openingNode != null && closingNode != null) {
            doc.append(openingNode) {
                it.oneSpace()
                it.highPriority()
            }
            doc.prepend(closingNode) {
                it.oneSpace()
                it.highPriority()
            }
        }
    }

    fun formatSkewedOpeningClosing(
        opening: ParserRuleContext,
        node: ParserRuleContext,
        doc: FormattableDocument,
        pair: BracePairTypes
    ) {
        val closing = tokenFor(node, pair.closing) ?: throw RellFormatterException("No closing bracket")
        doc.append(opening) {
            it.noSpace()
            it.highPriority()
        }
        doc.prepend(closing) {
            it.noSpace()
            it.highPriority()
        }
    }

    fun prependNodeList(
        firstNode: ParserRuleContext,
        nodeList: List<ParserRuleContext>?
    ): List<ParserRuleContext> {
        val expressions = mutableListOf<ParserRuleContext>()
        expressions.add(firstNode)
        if (nodeList != null) {
            expressions.addAll(nodeList)
        }
        return expressions
    }


    fun formatSemicolon(node: ParserRuleContext, doc: FormattableDocument) {
        val semiColon = tokenFor(node, ";")
        if (semiColon != null) {
            doc.prepend(semiColon) {
                it.noSpace()
                it.highPriority()
            }
        }
    }

    fun formatEqualSign(node: ParserRuleContext, doc: FormattableDocument) {
        val equalSign = tokenFor(node, "=")
        if (equalSign != null) {
            doc.surround(equalSign) {
                it.oneSpace()
                it.highPriority()
            }
        }
    }

    fun tokenFor(node: ParserRuleContext, tokenText: String): TerminalNode? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child is TerminalNode) {
                val token = child.symbol
                if (token.text == tokenText) {
                    return child
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child is ParserRuleContext) {
                val token = tokenFor(child, tokenText)
                if (token != null) {
                    return token
                }
            }
        }
        return null
    }

    fun nextSemanticRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        if (token.tokenIndex < 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null
        return commonTokenStream.get(token.tokenIndex + 1)
    }

    fun previousSemanticRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        if (token.tokenIndex < 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null
        return commonTokenStream.get(token.tokenIndex - 1)
    }

    fun nextHiddenRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToRight(token.tokenIndex, RellLexer.HIDDEN)?.firstOrNull()
    }

    fun previousHiddenRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToLeft(token.tokenIndex, RellLexer.HIDDEN)?.lastOrNull()
    }

    fun previousHiddenRegionList(token: Token): List<Token> {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToLeft(token.tokenIndex, RellLexer.HIDDEN) ?: listOf()
    }


    fun nextHiddenRegionList(token: Token): List<Token> {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToRight(token.tokenIndex, RellLexer.HIDDEN) ?: listOf()
    }
}
