package net.postchain.rell.toolbox.formatter

import net.postchain.rell.toolbox.parser.RellCustomTokenChannels
import net.postchain.rell.toolbox.parser.RellLexer
import net.postchain.rell.toolbox.parser.RellParser
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AnnotatedDefContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AnnotationArgContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AnnotationArgsContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AtExprFromContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AtExprFromItemContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AtExprWhatComplexContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AtExprWhatComplexItemContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AtExprWhereContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseAttributeDefinitionContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprHeadContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprTailAtContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprTailCallContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprTailContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprTailMemberContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BinaryExprOperandContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CallArgContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CallArgsContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CommaSeparated_10Context
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CommaSeparated_22Context
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CommaSeparated_24Context
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CommaSeparated_30Context
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CommaSeparated_3Context
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CreateExprArgContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CreateExprContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_EntityAnnotationsContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_EnumDefContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_EnumValueContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_ExpressionRefContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_FormalParameterContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_FormalParametersContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_GenericTypeContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_KeyIndexClauseContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_ListLiteralExprContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_MapLiteralExprEntryContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_NameContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_NonEmptyMapLiteralExprContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_TupleExprContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_TupleExprFieldContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_TupleVarDeclaratorContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_TypeRefContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_UnaryExprContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_UpdateStmtContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_UpdateWhatExprContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_VarDeclaratorContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_tkCOMMAContext
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

@Suppress("TooManyFunctions")
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
            is RuleX_BaseExprTailMemberContext -> {
                doc.prepend(currentExpr.ruleX_Name()) { p -> p.noSpace() }
                doc.append(currentExpr) { p -> p.noSpace() }
                doc.prepend(currentExpr) { p -> p.newLine() }
            }

            is RuleX_BaseExprTailContext -> {
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
        currentExpr: RuleX_BaseExprTailContext,
        previousExpr: ParserRuleContext?,
        doc: FormattableDocument,
        indent: Boolean = true
    ) {
        if (previousExpr is RuleX_BaseExprTailContext) {
            doc.interiorIndent(currentExpr)
        }
        val callArgs = currentExpr.ruleX_BaseExprTailCall().ruleX_CallArgs()
        formatBracePairWithoutSpace(callArgs, doc, BracePairTypes.PARENTHESES)
        val (callArg, trailingComma) = callArgs.getCallArgWithTrailingComma()
        val lineSeparate = formatCallArgsAsMultiLine(callArgs)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(callArg, doc, formatAsMultiLine = lineSeparate, indent = indent)
        doc.format(callArgs)
    }

    fun formatExprTailSingleline(xBaseExprTail: ParserRuleContext, doc: FormattableDocument) {
        if (xBaseExprTail is RuleX_BaseExprTailMemberContext) {
            doc.prepend(xBaseExprTail) { p -> p.noSpace() }
            doc.append(xBaseExprTail) { p -> p.noSpace() }
        } else if (xBaseExprTail is RuleX_BaseExprTailCallContext) {
            doc.append(xBaseExprTail) { p -> p.noSpace() }
            val callArgs = xBaseExprTail.ruleX_CallArgs()
            formatBracePairWithoutSpace(callArgs, doc, BracePairTypes.PARENTHESES)
            val (callArg, trailingComma) = callArgs.getCallArgWithTrailingComma()
            val lineSeparate = formatAsMultiLine(callArg)
            formatTrailingComma(trailingComma, doc, lineSeparate)
            formatArguments(callArg, doc)
            doc.format(xBaseExprTail.ruleX_CallArgs())
        } else if (xBaseExprTail is RuleX_BaseExprTailContext) {
            if (xBaseExprTail.ruleX_BaseExprTailCall() != null) {
                formatExprTailSingleline(xBaseExprTail.ruleX_BaseExprTailCall(), doc)
            } else if (xBaseExprTail.ruleX_BaseExprTailMember() != null) {
                formatExprTailSingleline(xBaseExprTail.ruleX_BaseExprTailMember(), doc)
            } else {
                doc.format(xBaseExprTail)
            }
        } else {
            doc.format(xBaseExprTail)
        }
    }

    fun formatMultiLineStmts(
        xUnaryExpr: RuleX_UnaryExprContext,
        stmts: List<RuleX_BinaryExprOperandContext>,
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

        return getLineLength(currentExpr) > formatterOptions.maxLineWidth || isLineSeparated
    }

    fun lineSeparateExpr(currentExpr: Token?, previousExp: Token?): Boolean {
        if (currentExpr == null && previousExp == null) {
            return false
        }
        val previousExprLineNr = previousExp!!.line
        val currentExprLineNr = currentExpr!!.line
        val isLineSeperated = previousExprLineNr != currentExprLineNr
        return getLineLength(currentExpr) > formatterOptions.maxLineWidth || isLineSeperated
    }

    fun lineSeparateArguments(methodDef: ParserRuleContext, pair: BracePairTypes): Boolean {
        val (bracketOpening, bracketClosing) = bracePairFor(methodDef, pair)
        var isLineSeperated = false
        if (bracketOpening != null && bracketClosing != null) {
            val openingLine = bracketOpening.symbol.line
            val closingLine = bracketClosing.symbol.line
            isLineSeperated = openingLine != closingLine
        }
        return getLineLength(methodDef) > formatterOptions.maxLineWidth || isLineSeperated
    }

    private fun exceedsMaxLineWidth(node: ParserRuleContext): Boolean {
        return getLineLength(node) > formatterOptions.maxLineWidth
    }

    private fun getLineLength(node: ParserRuleContext): Int {
        val lineStartOffset = node.start.startIndex - node.start.charPositionInLine
        val lineEndOffset = source.indexOf('\n', node.start.startIndex)
        return if (lineEndOffset != -1) lineEndOffset - lineStartOffset else node.text.length
    }

    private fun getLineLength(node: Token): Int {
        val lineStartOffset = node.startIndex - node.charPositionInLine
        val lineEndOffset = source.indexOf('\n', node.startIndex)
        return if (lineEndOffset != -1) lineEndOffset - lineStartOffset else node.text.length
    }

    fun formatAsMultiLine(args: List<ParserRuleContext>?): Boolean {
        var lineLength = 0
        if (args == null) {
            return false
        }
        if (args.isNotEmpty()) {
            lineLength = getLineLength(args[0])
        }
        return formatAsMultiLine(args, lineLength)
    }

    private fun formatAsMultiLine(args: List<ParserRuleContext>, length: Int): Boolean {
        var isMultiLine = false
        val nrOfArgs: Int = args.size
        if (nrOfArgs > 0) {
            val defStartLine = args[0].start.line
            val defEndLine = args[nrOfArgs - 1].stop.line
            isMultiLine = defStartLine != defEndLine
        }
        return length > formatterOptions.maxLineWidth || isMultiLine
    }

    private fun formatCallArgsAsMultiLine(callArgs: RuleX_CallArgsContext): Boolean {
        val (callArg, _) = callArgs.getCallArgWithTrailingComma()
        return formatAsMultiLine(callArg) || callArgs.start.line != callArgs.stop.line
    }

    fun formatParametersType(params: List<RuleX_FormalParameterContext>?, doc: FormattableDocument) {
        if (params.isNullOrEmpty()) {
            return
        }
        for (param in params) {
            formatType(param.ruleX_AttrHeader(), doc)
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
            formatAsMultiLine(args, length)
        } else {
            formatAsMultiLine(args)
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

    // Handle internal block indents for expression tail
    fun indentExpressionTail(
        exprHead: RuleX_BaseExprHeadContext,
        exprTailList: List<RuleX_BaseExprTailContext>,
        doc: FormattableDocument
    ) {
        val shouldLineSeparateTail = lineSeparateExpr(exprHead, exprTailList.last())
        if (shouldLineSeparateTail && exprTailList.first().ruleX_BaseExprTailAt() == null) {
            if (tailOnlyConsistOfOneTailCall(exprTailList)) {
                doc.interiorIndentRange(exprHead, exprTailList.last())
            } else if (tailHasMemberAndEndsWithTailCall(exprTailList)) {
                if (exprHead.stop.line != exprTailList.last().start.line || exceedsMaxLineWidth(exprTailList.last())) {
                    doc.interiorIndentRangeIncludeLast(exprHead, exprTailList.last())
                }
            } else if (tailEndsWithAtExpression(exprTailList)) {
                indentTailAtExpression(exprTailList.last().ruleX_BaseExprTailAt(), doc)
                if (shouldIndentBeforeAt(exprTailList, exprHead) || exceedsMaxLineWidth(exprTailList.last())) {
                    doc.prepend(exprTailList[exprTailList.lastIndex - 1]) { it.indent() }
                }
            } else {
                doc.interiorIndentRangeIncludeLast(exprHead, exprTailList.last())
            }
        }
    }

    private fun tailOnlyConsistOfOneTailCall(exprTailList: List<RuleX_BaseExprTailContext>): Boolean {
        return exprTailList.size == 1 && exprTailList.first().ruleX_BaseExprTailCall() != null
    }

    private fun tailHasMemberAndEndsWithTailCall(exprTailList: List<RuleX_BaseExprTailContext>): Boolean {
        return exprTailList.size == 2 && exprTailList.last().ruleX_BaseExprTailCall() != null
    }

    private fun tailEndsWithAtExpression(exprTailList: List<RuleX_BaseExprTailContext>): Boolean {
        return exprTailList.last().ruleX_BaseExprTailAt() != null
    }

    private fun shouldIndentBeforeAt(
        exprTailList: List<RuleX_BaseExprTailContext>,
        exprHead: RuleX_BaseExprHeadContext
    ): Boolean {
        return when (exprTailList.size) {
            1 -> false
            2 -> exprHead.stop.line != exprTailList.first().start.line
            else -> {
                val lastIndex = exprTailList.lastIndex
                exprTailList[lastIndex - 2].stop.line != exprTailList[lastIndex - 1].start.line
            }
        }
    }

    private fun indentTailAtExpression(tailAt: RuleX_BaseExprTailAtContext, doc: FormattableDocument) {
        val whereExpr = tailAt.ruleX_AtExprWhere()
        val (expressionRef, trailingComma) = whereExpr.getExpressionRefWithTrailingComma()
        formatTrailingComma(trailingComma, doc)
        if (whereExpr != null && formatAsMultiLine(expressionRef)) {
            doc.interiorIndentRangeIncludeLast(whereExpr, whereExpr)
        }

        val whatExpr = tailAt.ruleX_AtExprWhat()
        if (whatExpr != null) {
            if (whatExpr.start.line != whatExpr.stop.line || exceedsMaxLineWidth(whatExpr)) {
                doc.interiorIndentRangeIncludeLast(whatExpr, whatExpr)
            }
        }
    }

    fun formatType(node: ParserRuleContext, doc: FormattableDocument) {
        val paramTypeDef = tokenFor(node, ":")
        doc.prepend(paramTypeDef) { p -> p.noSpace() }
        doc.append(paramTypeDef) { p -> p.oneSpace() }
    }

    fun formatOpeningClosingLines(
        definitions: List<RuleX_AnnotatedDefContext>,
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
                it.setNewLines(newLines)
                it.noSpace()
                it.highPriority()
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
        val (openingNode, closingNode) = bracePairFor(node, pair)

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
        val (openingNode, closingNode) = bracePairFor(node, pair)
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

    fun bracePairFor(node: ParserRuleContext?, pairTypes: BracePairTypes): Pair<TerminalNode?, TerminalNode?> {
        node ?: return Pair(null, null)
        val allBraces = mutableListOf<TerminalNode>()
        findAllBraces(node, pairTypes, allBraces)

        return findMatchingOpenClosing(allBraces, pairTypes)
    }

    private fun findMatchingOpenClosing(
        allBraces: MutableList<TerminalNode>,
        pairTypes: BracePairTypes
    ): Pair<TerminalNode?, TerminalNode?> {
        var braceCount = 0
        var openingBrace: TerminalNode? = null

        for (brace in allBraces) {
            if (brace.symbol.text == pairTypes.opening) {
                if (openingBrace == null) openingBrace = brace
                braceCount++
            } else {
                braceCount--
                if (braceCount == 0 && openingBrace != null) {
                    return Pair(openingBrace, brace)
                }
            }
        }
        return Pair(null, null)
    }

    private fun findAllBraces(node: ParserRuleContext, pairTypes: BracePairTypes, braces: MutableList<TerminalNode>) {
        node.children?.forEach { child ->
            when (child) {
                is TerminalNode -> {
                    if (child.symbol.text in setOf(pairTypes.opening, pairTypes.closing)) {
                        braces.add(child)
                    }
                }
                is ParserRuleContext -> findAllBraces(child, pairTypes, braces)
            }
        }
    }

    fun tokenFor(node: ParserRuleContext?, tokenText: String): TerminalNode? {
        if (node == null) {
            return null
        }
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
        if (token.tokenIndex <= 0 || token.tokenIndex >= commonTokenStream.tokens.size) return null
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

    fun previousCommentRegion(token: Token): Token? {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToLeft(token.tokenIndex, RellCustomTokenChannels.COMMENTS.channel)
            ?.lastOrNull()
    }

    private fun previousHiddenRegionList(token: Token): List<Token> {
        val commonTokenStream = parser.tokenStream as CommonTokenStream
        return commonTokenStream.getHiddenTokensToLeft(token.tokenIndex, RellLexer.HIDDEN) ?: listOf()
    }

    internal fun RuleX_CallArgsContext.getCallArgWithTrailingComma():
        Pair<List<RuleX_CallArgContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_28()?.ruleX_CommaSeparated_27()
        return Pair(commaSeparated?.ruleX_CallArg(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_AtExprWhereContext.getExpressionRefWithTrailingComma():
        Pair<List<RuleX_ExpressionRefContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_20()?.ruleX_CommaSeparated_19()
        return Pair(commaSeparated?.ruleX_ExpressionRef(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_FormalParametersContext.getFormalParameterWithTrailingComma():
        Pair<List<RuleX_FormalParameterContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_36()?.ruleX_CommaSeparated_35()
        return Pair(commaSeparated?.ruleX_FormalParameter(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_EnumDefContext.getXNamesWithTrailingComma():
        Pair<List<RuleX_EnumValueContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_12()?.ruleX_CommaSeparated_11()
        return Pair(commaSeparated?.ruleX_EnumValue(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_KeyIndexClauseContext.getAttributeDefsWithTrailingComma():
        Pair<List<RuleX_BaseAttributeDefinitionContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_8()
        return Pair(commaSeparated?.ruleX_BaseAttributeDefinition(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_AnnotationArgsContext.getAnnotationArgWithTrailingComma():
        Pair<List<RuleX_AnnotationArgContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_7()?.ruleX_CommaSeparated_6()
        return Pair(commaSeparated?.ruleX_AnnotationArg(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_CreateExprContext.getCreateExprArgWithTrailingComma():
        Pair<List<RuleX_CreateExprArgContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CreateExprArgs()?.ruleX_CommaSeparated_26()?.ruleX_CommaSeparated_25()
        return Pair(commaSeparated?.ruleX_CreateExprArg(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_UpdateStmtContext.getUpdateWhatExprWithTrailingComma():
        Pair<List<RuleX_UpdateWhatExprContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_UpdateWhat()?.ruleX_CommaSeparated_34()?.ruleX_CommaSeparated_33()
        return Pair(commaSeparated?.ruleX_UpdateWhatExpr(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_AtExprFromContext.getAtExprFromItemWithTrailingComma():
        Pair<List<RuleX_AtExprFromItemContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_16()?.ruleX_CommaSeparated_15()
        return Pair(commaSeparated?.ruleX_AtExprFromItem(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_AtExprWhatComplexContext.getAtExprWhatComplexItemWithTrailingComma():
        Pair<List<RuleX_AtExprWhatComplexItemContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_18()?.ruleX_CommaSeparated_17()
        return Pair(commaSeparated?.ruleX_AtExprWhatComplexItem(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_AtExprWhatComplexItemContext.getBaseExpr(): RuleX_BaseExprContext? =
        ruleX_ExpressionRef()?.ruleX_Expression()?.ruleX_UnaryExpr()?.ruleX_OperandExpr()?.ruleX_BaseExpr()

    internal fun RuleX_TupleVarDeclaratorContext.getTupleVarContext(): RuleX_CommaSeparated_30Context =
        ruleX_CommaSeparated_30()

    internal fun RuleX_TupleVarDeclaratorContext.getVarDeclaratorWithTrailingComma():
        Pair<List<RuleX_VarDeclaratorContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_30()?.ruleX_CommaSeparated_29()
        return Pair(commaSeparated?.ruleX_VarDeclarator(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_NonEmptyMapLiteralExprContext.getMapExprContext(): RuleX_CommaSeparated_24Context =
        ruleX_CommaSeparated_24()

    internal fun RuleX_NonEmptyMapLiteralExprContext.getMapExprEntryWithTrailingComma():
        Pair<List<RuleX_MapLiteralExprEntryContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_24()?.ruleX_CommaSeparated_23()
        return Pair(commaSeparated?.ruleX_MapLiteralExprEntry(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_GenericTypeContext.getGenericTypeContext(): RuleX_CommaSeparated_3Context =
        ruleX_CommaSeparated_3()

    internal fun RuleX_GenericTypeContext.getTypeRefWithTrailingComma():
        Pair<List<RuleX_TypeRefContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_3()?.ruleX_CommaSeparated_2()
        return Pair(commaSeparated?.ruleX_TypeRef(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_ListLiteralExprContext.getListLiteralExprContext(): RuleX_CommaSeparated_22Context =
        ruleX_CommaSeparated_22()

    internal fun RuleX_ListLiteralExprContext.getExpressionRefWithTrailingComma():
        Pair<List<RuleX_ExpressionRefContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_22()?.ruleX_CommaSeparated_21()
        return Pair(commaSeparated?.ruleX_ExpressionRef(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_EntityAnnotationsContext.getEntityAnnotationContext():
        RuleX_CommaSeparated_10Context = ruleX_CommaSeparated_10()

    internal fun RuleX_EntityAnnotationsContext.getXNamesWithTrailingComma():
        Pair<List<RuleX_NameContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_10()?.ruleX_CommaSeparated_9()
        return Pair(commaSeparated?.ruleX_Name(), commaSeparated?.ruleX_tkCOMMA())
    }

    internal fun RuleX_TupleExprContext.getXNamesWithTrailingComma():
        Pair<List<RuleX_TupleExprFieldContext>?, RuleX_tkCOMMAContext?> {
        val commaSeparated = ruleX_CommaSeparated_14()?.ruleX_CommaSeparated_13()
        return Pair(commaSeparated?.ruleX_TupleExprField(), commaSeparated?.ruleX_tkCOMMA())
    }
}
