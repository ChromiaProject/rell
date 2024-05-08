package net.postchain.rell.toolbox.formatter

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.core.parser.RellLexer
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.RellParser.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext

class RellFormatter(parser: RellParser, source: String, formatterRequest: FormatterOptions) :
    RellAbstractFormatter(parser, source, formatterRequest) {

    private val logger = KotlinLogging.logger {}

    fun format(rootNode: RuleX_RootParserContext, doc: FormattableDocument) {
        doc.format(rootNode.ruleX_ModuleHeader())
        formatOpeningClosingLines(rootNode.ruleX_AnnotatedDef(), doc)
        rootNode.ruleX_AnnotatedDef().forEach { xAnnotedDef ->
            doc.format(xAnnotedDef)
        }
    }

    fun format(xRootParser: RuleX_ModuleHeaderContext, doc: FormattableDocument) {
        formatSemicolon(xRootParser, doc)
        doc.prepend(xRootParser) { it.noSpace() }
        doc.append(xRootParser) {
            it.setNewLines(2)
            it.superHighPriority()
        }
    }

    fun format(xFunctionDef: RuleX_FunctionDefContext, doc: FormattableDocument) {
        doc.surround(xFunctionDef) { it.setNewLines(2) }
        doc.prepend(xFunctionDef.ruleX_QualifiedName()) { it.oneSpace() }
        doc.append(xFunctionDef.ruleX_QualifiedName()) { it.noSpace() }
        formatBracePairWithoutSpace(xFunctionDef, doc, BracePairTypes.PARENTHESES)
        formatType(xFunctionDef, doc)
        val lineSeparate = lineSeparateArguments(xFunctionDef, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = xFunctionDef.ruleX_FormalParameters()
            .getFormalParameterWithTrailingComma()

        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(
            formalParameters,
            doc,
            formatAsMultiLine = lineSeparate
        )
        formatParametersType(formalParameters, doc)
        doc.format(xFunctionDef.ruleX_FunctionBody())
    }

    fun format(xFunctionBodyShort: RuleX_FunctionBodyShortContext, doc: FormattableDocument) {
        formatSemicolon(xFunctionBodyShort, doc)
        doc.format(xFunctionBodyShort.ruleX_Expression())
    }

    fun format(xBlockStmt: RuleX_BlockStmtContext, doc: FormattableDocument) {
        doc.interiorIndent(xBlockStmt)
        doc.prepend(xBlockStmt.ruleX_tkLCURL()) { it.oneSpace() }
        val statements = xBlockStmt.ruleX_StatementRef()

        statements.forEachIndexed { index, statement ->
            doc.prepend(statement) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
            if (index == statements.lastIndex) {
                doc.append(statement) { it.newLine() }
            }
            doc.format(statement)
        }
    }

    fun format(xReturnStmt: RuleX_ReturnStmtContext, doc: FormattableDocument) {
        formatSemicolon(xReturnStmt, doc)
        doc.append(xReturnStmt.ruleX_tkRETURN()) { it.oneSpace() }
        doc.format(xReturnStmt.ruleX_Expression())
    }

    fun format(xQueryDef: RuleX_QueryDefContext, doc: FormattableDocument) {
        doc.surround(xQueryDef) { it.setNewLines(2) }
        doc.prepend(xQueryDef.ruleX_Name()) { it.oneSpace() }
        doc.append(xQueryDef.ruleX_Name()) { it.noSpace() }
        formatBracePairWithoutSpace(xQueryDef, doc, BracePairTypes.PARENTHESES)
        formatType(xQueryDef, doc)
        val lineSeparate = lineSeparateArguments(xQueryDef, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = xQueryDef.ruleX_FormalParameters().getFormalParameterWithTrailingComma()
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(formalParameters, doc, formatAsMultiLine = lineSeparate)
        formatParametersType(formalParameters, doc)
        doc.format(xQueryDef.ruleX_QueryBody())
    }

    fun format(xOpDef: RuleX_OpDefContext, doc: FormattableDocument) {
        doc.surround(xOpDef) { it.setNewLines(2, 2, 2) }
        doc.prepend(xOpDef.ruleX_Name()) { it.oneSpace() }
        doc.append(xOpDef.ruleX_Name()) { it.noSpace() }
        formatBracePairWithoutSpace(xOpDef, doc, BracePairTypes.PARENTHESES)
        formatType(xOpDef, doc)
        val lineSeparate = lineSeparateArguments(xOpDef, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = xOpDef.ruleX_FormalParameters().getFormalParameterWithTrailingComma()
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(formalParameters, doc, formatAsMultiLine = lineSeparate)
        formatParametersType(formalParameters, doc)
        doc.format(xOpDef.ruleX_BlockStmt())
    }

    fun format(xBaseExpr: RuleX_BaseExprContext, doc: FormattableDocument) {
        val exprHead = xBaseExpr.ruleX_BaseExprHead()
        val exprTailList = xBaseExpr.ruleX_BaseExprTail()
        var shouldLineSeparateExpression: Boolean
        var previousExpr: ParserRuleContext? = exprHead

        doc.append(exprHead) {
            it.noSpace()
            it.lowPriority()
        }

        if (exprTailList.isNotEmpty()) {
            indentExpressionTail(exprHead, exprTailList, doc)
        }

        for (i in 0 until exprTailList.size) {
            val currentExpr = exprTailList[i]
            if (i > 0) {
                previousExpr = exprTailList[i - 1]
                shouldLineSeparateExpression = lineSeparateExpr(currentExpr, previousExpr)
            } else {
                shouldLineSeparateExpression = lineSeparateExpr(currentExpr, exprHead)
            }
            if (shouldLineSeparateExpression) {
                formatExprTailMultiline(currentExpr, previousExpr, doc)
            } else {
                formatExprTailSingleline(currentExpr, doc)
            }
        }
        doc.format(exprHead)
    }

    fun format(xTupleExprContext: RuleX_TupleExprContext, doc: FormattableDocument) {
        val xTupleExpr = xTupleExprContext.ruleX_CommaSeparated_14()
        val opening = xTupleExpr.ruleX_tkLPAR()
        val closing = tokenFor(xTupleExpr, ")")
        val lineSeparateExpression = lineSeparateExpr(opening?.start, closing?.symbol)
        formatSkewedOpeningClosing(opening, xTupleExpr, doc, BracePairTypes.PARENTHESES)

        val commaSeparateExpr13 = xTupleExpr.ruleX_CommaSeparated_13()
        val (tupleExprField, trailingComma) = xTupleExprContext.getXNamesWithTrailingComma()
        formatTrailingComma(trailingComma, doc, lineSeparateExpression)

        if (lineSeparateExpression) {
            doc.interiorIndent(xTupleExpr)
            doc.prepend(commaSeparateExpr13) { it.newLine() }
            doc.append(commaSeparateExpr13) { it.noSpace() }
            doc.format(commaSeparateExpr13)

            if (tupleExprField != null) {
                formatArguments(
                    tupleExprField,
                    doc,
                    indent = false
                )
            }
            doc.prepend(closing) { it.newLine() }
        } else {
            formatArguments(tupleExprField, doc)
        }
    }

    fun format(xAtExprMod: RuleX_AtExprModifiers_0Context, doc: FormattableDocument) {
        val offsetExpr = xAtExprMod.ruleX_AtExprOffset()
        val limit = tokenFor(xAtExprMod, "limit")
        val offset = tokenFor(offsetExpr, "offset")
        if (limit != null) {
            doc.surround(limit) { it.oneSpace() }
        }
        if (offset != null) {
            doc.surround(offset) { it.oneSpace() }
        }
        doc.format(xAtExprMod.ruleX_ExpressionRef())
        doc.format(xAtExprMod.ruleX_AtExprOffset())
    }

    fun format(xAtExprMod: RuleX_AtExprModifiers_1Context, doc: FormattableDocument) {
        val limitExpr = xAtExprMod.ruleX_AtExprLimit()
        val offset = tokenFor(xAtExprMod, "offset")
        val limit = tokenFor(limitExpr, "limit")
        if (limit != null) doc.surround(limit) { it.oneSpace() }
        if (offset != null) doc.surround(offset) { it.oneSpace() }
        doc.format(xAtExprMod.ruleX_ExpressionRef())
        doc.format(xAtExprMod.ruleX_AtExprLimit())
    }

    fun format(xIfStmt: RuleX_IfStmtContext, doc: FormattableDocument) {
        doc.append(xIfStmt.ruleX_tkIF()) { it.oneSpace() }
        doc.surround(xIfStmt.ruleX_Expression()) { it.noSpace() }
        val xExpression = xIfStmt.ruleX_Expression()
        if (formatAsMultiLine(prependNodeList(xExpression, xExpression.ruleX_BinaryExprOperand()))) {
            formatMultiLineStmts(xExpression.ruleX_UnaryExpr(), xExpression.ruleX_BinaryExprOperand(), doc)
        } else {
            doc.format(xExpression)
        }

        if (xIfStmt.ruleX_tkIF().stop.line != xIfStmt.ruleX_StatementRef().start.line) {
            val openingCurly = tokenFor(xIfStmt.ruleX_StatementRef(), "{")
            doc.prepend(openingCurly) {
                it.oneSpace()
                it.setNewLines(0)
                it.highPriority()
            }
            doc.prepend(xIfStmt.ruleX_StatementRef()) {
                it.newLine();
            }
            if (openingCurly == null) {
                doc.prepend(xIfStmt.ruleX_StatementRef()) {
                    it.indent();
                }
                doc.interiorIndentRangeIncludeLast(
                    xIfStmt.ruleX_StatementRef(),
                    xIfStmt.ruleX_StatementRef()
                )
            }
        } else {
            doc.prepend(xIfStmt.ruleX_StatementRef()) { it.oneSpace() }
        }

        val elseStatement = xIfStmt.ruleX_ElseStmt()
        if (elseStatement?.ruleX_tkELSE() != null && elseStatement.ruleX_StatementRef() != null) {
            if (elseStatement.ruleX_tkELSE().stop.line != elseStatement.ruleX_StatementRef().start.line) {
                doc.prepend(elseStatement.ruleX_tkELSE()) { it.newLine() }
                doc.prepend(elseStatement.ruleX_StatementRef()) {
                    it.newLine();
                    it.indent();
                }
                doc.interiorIndentRangeIncludeLast(
                    elseStatement.ruleX_StatementRef(),
                    elseStatement.ruleX_StatementRef()
                )
                doc.append(elseStatement.ruleX_StatementRef()) { it.noSpace() }
            } else {
                doc.prepend(elseStatement.ruleX_tkELSE()) { it.oneSpace() }
                doc.prepend(elseStatement.ruleX_StatementRef()) {
                    it.oneSpace()
                    it.highPriority()
                }
            }
        }
        doc.format(xIfStmt.ruleX_StatementRef())
        doc.format(xIfStmt.ruleX_ElseStmt())
    }

    fun format(xIfExpr: RuleX_IfExprContext, doc: FormattableDocument) {
        val checkExpr = xIfExpr.ruleX_ExpressionRef(0)
        val conditionalIfExpr = xIfExpr.ruleX_ExpressionRef(1)
        val conditionalElseExpr = xIfExpr.ruleX_ExpressionRef(2)

        doc.surround(xIfExpr.ruleX_tkIF()) { it.oneSpace() }
        doc.surround(checkExpr) { it.noSpace() }
        doc.format(checkExpr)

        if (checkExpr.stop.line != conditionalIfExpr.start.line) {
            doc.prepend(conditionalIfExpr) {
                it.newLine();
                it.indent();
                it.highPriority()
            }
            doc.interiorIndentRangeIncludeLast(conditionalIfExpr, conditionalIfExpr)
            doc.append(conditionalIfExpr) { it.newLine() }
        } else {
            doc.surround(conditionalIfExpr) {
                it.oneSpace()
                it.highPriority()
            }
        }


        val elseKeyword = tokenFor(xIfExpr, "else")
        if (elseKeyword != null) {
            if (elseKeyword.symbol.line != conditionalElseExpr.start.line) {
                doc.prepend(conditionalElseExpr) {
                    it.newLine();
                    it.indent();
                    it.highPriority()
                }
                doc.append(conditionalElseExpr) { it.noSpace() }
            } else {
                doc.surround(elseKeyword) { it.oneSpace() }
                doc.prepend(conditionalElseExpr) {
                    it.oneSpace()
                    it.highPriority()
                }
                doc.append(conditionalElseExpr) {
                    it.oneSpace()
                    it.lowPriority()
                }
            }
        }

        doc.format(conditionalIfExpr)
        doc.format(conditionalElseExpr)
    }

    fun format(xWhileStmt: RuleX_WhileStmtContext, doc: FormattableDocument) {
        doc.append(xWhileStmt.ruleX_tkWHILE()) { it.oneSpace() }
        doc.surround(xWhileStmt.ruleX_Expression()) { it.noSpace() }
        val xExpression = xWhileStmt.ruleX_Expression()
        if (formatAsMultiLine(prependNodeList(xExpression, xExpression.ruleX_BinaryExprOperand()))) {
            formatMultiLineStmts(xExpression.ruleX_UnaryExpr(), xExpression.ruleX_BinaryExprOperand(), doc)
        } else {
            doc.format(xExpression)
        }
        doc.prepend(xWhileStmt.ruleX_StatementRef()) { it.oneSpace() }
        doc.format(xWhileStmt.ruleX_StatementRef())
    }

    fun format(xForStmt: RuleX_ForStmtContext, doc: FormattableDocument) {
        doc.append(xForStmt.ruleX_tkFOR()) { it.oneSpace() }
        doc.prepend(xForStmt.ruleX_VarDeclarator()) { it.noSpace() }
        doc.append(xForStmt.ruleX_Expression()) { it.noSpace() }
        doc.prepend(xForStmt.ruleX_StatementRef()) { it.oneSpace() }
        doc.format(xForStmt.ruleX_StatementRef())
    }

    fun format(xWhenStmt: RuleX_WhenStmtContext, doc: FormattableDocument) {
        doc.interiorIndent(xWhenStmt)
        doc.append(xWhenStmt.ruleX_tkWHEN()) { it.oneSpace() }
        doc.surround(xWhenStmt.ruleX_ExpressionRef()) { it.noSpace() }
        doc.format(xWhenStmt.ruleX_ExpressionRef())
        val openingCurly = tokenFor(xWhenStmt, "{")
        doc.prepend(openingCurly) { it.oneSpace() }
        for (whenCase in xWhenStmt.ruleX_WhenStmtCase()) {
            doc.prepend(whenCase) { it.newLine() }
            doc.append(whenCase.ruleX_WhenCondition()) {
                it.oneSpace()
                it.highPriority()
            }
            doc.format(whenCase.ruleX_WhenCondition())
            doc.prepend(whenCase.ruleX_StatementRef()) { it.oneSpace() }
            doc.format(whenCase.ruleX_StatementRef())
        }
        val closingCurly = tokenFor(xWhenStmt, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }

    fun format(xWhenExpr: RuleX_WhenExprContext, doc: FormattableDocument) {
        doc.interiorIndent(xWhenExpr)
        doc.append(xWhenExpr.ruleX_tkWHEN()) { it.oneSpace() }
        doc.surround(xWhenExpr.ruleX_ExpressionRef()) { it.noSpace() }
        doc.format(xWhenExpr.ruleX_ExpressionRef())
        val openingCurly = tokenFor(xWhenExpr, "{")
        doc.prepend(openingCurly) { it.oneSpace() }
        val closingCurly = tokenFor(xWhenExpr, "}")
        doc.prepend(closingCurly) { it.newLine() }
        doc.format(xWhenExpr.ruleX_WhenExprCases())
    }

    fun format(xWhenExprCases: RuleX_WhenExprCasesContext, doc: FormattableDocument) {
        for (whenCase in xWhenExprCases.ruleX_WhenExprCase()) {
            doc.prepend(whenCase) { it.newLine() }
            doc.append(whenCase.ruleX_WhenCondition()) {
                it.oneSpace()
                it.highPriority()
            }
            doc.format(whenCase.ruleX_WhenCondition())
            doc.prepend(whenCase.ruleX_ExpressionRef()) { it.oneSpace() }
            doc.format(whenCase.ruleX_ExpressionRef())
        }
    }

    fun format(xWhenCondExpr: RuleX_WhenConditionExprContext, doc: FormattableDocument) {
        val expressions = xWhenCondExpr.ruleX_ExpressionRef()
        expressions.forEachIndexed { index, xExprRef ->
            doc.prepend(xExprRef) { it.oneSpace() }
            if (index == expressions.lastIndex) {
                doc.append(xExprRef) { it.noSpace() }
            }
            doc.format(xExprRef)
        }
    }

    fun format(xBinaryOp: RuleX_BinaryOperatorContext, doc: FormattableDocument) {
        doc.surround(xBinaryOp) {
            it.oneSpace()
            it.highPriority()
        }
    }

    fun format(xBinOp16: RuleX_BinaryOperator_16Context, doc: FormattableDocument) {
        doc.surround(xBinOp16) {
            it.oneSpace()
            it.highPriority()
        }
        doc.surround(xBinOp16.ruleX_tkIN()) {
            it.oneSpace()
            it.highPriority()
        }
    }

    fun format(xObjectDef: RuleX_ObjectDefContext, doc: FormattableDocument) {
        doc.surround(xObjectDef) { it.setNewLines(2) }
        doc.interiorIndent(xObjectDef)
        doc.surround(xObjectDef.ruleX_Name()) { it.oneSpace() }

        for (xAttriDef in xObjectDef.ruleX_AttributeDefinition()) {
            formatSemicolon(xObjectDef, doc)
            formatEqualSign(xObjectDef, doc)
            doc.append(xAttriDef.ruleX_BaseAttributeDefinition().ruleX_AttrHeader()) {
                it.setNewLines(0)
                it.oneSpace()
            }
            doc.format(xAttriDef)
        }

        val closingCurly = tokenFor(xObjectDef, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }

    fun format(xObjectDef: RuleX_StructDefContext, doc: FormattableDocument) {
        doc.surround(xObjectDef) { it.setNewLines(2) }
        doc.interiorIndent(xObjectDef)
        doc.surround(xObjectDef.ruleX_Name()) { it.oneSpace() }
        for (xAttriDef in xObjectDef.ruleX_AttributeDefinition()) {
            formatSemicolon(xObjectDef, doc)
            doc.format(xAttriDef)
        }
        val closingCurly = tokenFor(xObjectDef, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }

    fun format(xEnumDef: RuleX_EnumDefContext, doc: FormattableDocument) {
        doc.surround(xEnumDef) { it.setNewLines(2, 2, 2) }
        doc.surround(xEnumDef.ruleX_Name()) { it.oneSpace() }

        val (xNames, trailingComma) = xEnumDef.getXNamesWithTrailingComma()
        val lineSeparate = formatAsMultiLine(xNames)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        xNames?.forEachIndexed { index, xName ->
            doc.prepend(xName) { it.newLine() }
            doc.surround(xName) { it.noSpace() }
            doc.prepend(xName) { it.indent() }

            if (index == xNames.lastIndex) {
                doc.append(xName) { it.newLine() }
            }
        }
        val closingCurly = tokenFor(xEnumDef, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }

    fun format(xRellKeyIndex: RuleX_RelKeyIndexClauseContext, doc: FormattableDocument) {
        doc.append(xRellKeyIndex) { it.noSpace() }
        doc.prepend(xRellKeyIndex) { it.newLine() }
        formatSemicolon(xRellKeyIndex, doc)

        val (attributeDefs, trailingComma) = xRellKeyIndex.getAttributeDefsWithTrailingComma()
        formatTrailingComma(trailingComma, doc)
        attributeDefs?.forEach { attributeDef ->
            doc.prepend(attributeDef) { it.oneSpace() }
            doc.append(attributeDef.ruleX_tkMUTABLE()) { it.oneSpace() }
            doc.format(attributeDef.ruleX_AttrHeader())
            doc.prepend(attributeDef.ruleX_ExpressionRef()) { it.oneSpace() }
            doc.append(attributeDef) { it.noSpace() }
        }
    }

    fun format(xBaseAttriDef: RuleX_BaseAttributeDefinitionContext, doc: FormattableDocument) {
        doc.append(xBaseAttriDef) { it.noSpace() }
        doc.prepend(xBaseAttriDef) { it.newLine() }
        doc.append(xBaseAttriDef.ruleX_tkMUTABLE()) { it.oneSpace() }
        doc.format(xBaseAttriDef.ruleX_AttrHeader())
        val equalSign = tokenFor(xBaseAttriDef, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.prepend(xBaseAttriDef.ruleX_ExpressionRef()) { it.oneSpace() }
    }

    fun format(xNameTypeAttrHead: RuleX_NameTypeAttrHeaderContext, doc: FormattableDocument) {
        doc.append(xNameTypeAttrHead.ruleX_Name()) { it.noSpace() }
        doc.prepend(xNameTypeAttrHead.ruleX_Type()) { it.oneSpace() }
    }

    fun format(xModifier: RuleX_ModifierContext, doc: FormattableDocument) {
        val annotation = xModifier.ruleX_Annotation()
        doc.append(annotation) {
            it.newLine()
            it.highPriority()
        }
        formatModifier(xModifier.ruleX_Modifier_0(), doc)
        formatModifier(xModifier.ruleX_Modifier_1(), doc)
    }

    fun format(xAnnotation: RuleX_AnnotationContext, doc: FormattableDocument) {
        doc.append(xAnnotation) {
            it.setNewLines(1)
            it.highPriority()
        }
        doc.append(xAnnotation.ruleX_Name()) { it.noSpace() }
        doc.format(xAnnotation.ruleX_AnnotationArgs())
    }

    fun format(xAnnotArgs: RuleX_AnnotationArgsContext, doc: FormattableDocument) {
        formatBracePairWithoutSpace(xAnnotArgs, doc, BracePairTypes.PARENTHESES)
        val (annotationArg, trailingComma) = xAnnotArgs.getAnnotationArgWithTrailingComma()
        val lineSeparate = formatAsMultiLine(annotationArg)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        if (lineSeparate) {
            doc.interiorIndentRangeIncludeLast(xAnnotArgs, xAnnotArgs)
        }
        formatArguments(annotationArg, doc, formatAsMultiLine = lineSeparate)
    }

    fun format(xNamespaceDef: RuleX_NamespaceDefContext, doc: FormattableDocument) {
        doc.surround(xNamespaceDef) { it.setNewLines(2) }
        doc.append(xNamespaceDef.ruleX_tkNAMESPACE()) { it.oneSpace() }
        doc.append(xNamespaceDef.ruleX_QualifiedName()) { it.oneSpace() }
        val openingCurly = tokenFor(xNamespaceDef, "{")
        if (openingCurly != null) {
            doc.append(openingCurly) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }
        doc.interiorIndent(xNamespaceDef)
        for (xAnnotDef in xNamespaceDef.ruleX_AnnotatedDef()) {
            doc.format(xAnnotDef)
        }
        val closingCurly = tokenFor(xNamespaceDef, "}")
        if (closingCurly != null) {
            doc.prepend(closingCurly) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }

        xNamespaceDef.ruleX_AnnotatedDef().forEach {
            doc.prepend(it) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }
    }

    fun format(xCreateExpr: RuleX_CreateExprContext, doc: FormattableDocument) {
        doc.append(xCreateExpr.ruleX_tkCREATE()) { it.oneSpace() }
        doc.append(xCreateExpr.ruleX_QualifiedName()) { it.oneSpace() }
        formatBracePairWithSpace(xCreateExpr, doc, BracePairTypes.PARENTHESES)
        val (createExprArg, trailingComma) = xCreateExpr.getCreateExprArgWithTrailingComma()
        val lineSeparate = lineSeparateArguments(xCreateExpr, BracePairTypes.PARENTHESES)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(createExprArg, doc, formatAsMultiLine = lineSeparate)
    }

    fun format(xUpdateStmt: RuleX_UpdateStmtContext, doc: FormattableDocument) {
        doc.append(xUpdateStmt.ruleX_tkUPDATE()) { it.oneSpace() }
        doc.append(xUpdateStmt.ruleX_UpdateTarget()) { it.oneSpace() }
        doc.format(xUpdateStmt.ruleX_UpdateTarget())
        formatBracePairWithSpace(xUpdateStmt, doc, BracePairTypes.PARENTHESES)
        val (whatExpr, trailingComma) = xUpdateStmt.getUpdateWhatExprWithTrailingComma()
        val lineSeparate = lineSeparateArguments(xUpdateStmt, BracePairTypes.PARENTHESES)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(whatExpr, doc, formatAsMultiLine = lineSeparate)
        formatSemicolon(xUpdateStmt, doc)
    }

    fun format(xAtExprFrom: RuleX_AtExprFromContext, doc: FormattableDocument) {
        val (atExprFromItem, trailingComma) = xAtExprFrom.getAtExprFromItemWithTrailingComma()
        val lineSeparate = lineSeparateArguments(xAtExprFrom, BracePairTypes.PARENTHESES)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(atExprFromItem, doc, formatAsMultiLine = lineSeparate)
        atExprFromItem?.forEach { doc.format(it) }
    }

    fun format(xAtExprFromItem: RuleX_AtExprFromItemContext, doc: FormattableDocument) {
        doc.append(tokenFor(xAtExprFromItem, ":")) { it.oneSpace() }
        xAtExprFromItem.ruleX_Annotation().forEach { xAnnotation ->
            doc.append(xAnnotation) { it.oneSpace() }
        }
        doc.interiorIndentRangeIncludeLast(xAtExprFromItem, xAtExprFromItem.ruleX_ExpressionRef())
        doc.format(xAtExprFromItem.ruleX_ExpressionRef())
    }

    fun format(xUpdateTargetAt: RuleX_UpdateTargetAtContext, doc: FormattableDocument) {
        //doc.append(xUpdateTargetAt.ruleX_AtExprFrom()) { it.oneSpace() }
        doc.append(xUpdateTargetAt.ruleX_UpdateFrom()) { it.oneSpace() }
        doc.append(xUpdateTargetAt.ruleX_AtExprAt()) { it.oneSpace() }

        //TODO: Format atExprWhere should work, duplication of code
        val atExprWhere = xUpdateTargetAt.ruleX_AtExprWhere()
        formatBracePairWithSpace(atExprWhere, doc, BracePairTypes.CURLY)
        val (expressionRef, trailingComma) = atExprWhere.getExpressionRefWithTrailingComma()
        val lineSeparate = lineSeparateArguments(atExprWhere, BracePairTypes.CURLY)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(expressionRef, doc, formatAsMultiLine = lineSeparate)
        expressionRef?.forEach { doc.format(it) }
    }

    fun format(xDeleteStmt: RuleX_DeleteStmtContext, doc: FormattableDocument) {
        doc.append(xDeleteStmt.ruleX_tkDELETE()) { it.oneSpace() }
        doc.format(xDeleteStmt.ruleX_UpdateTarget())
        formatSemicolon(xDeleteStmt, doc)
    }

    fun format(xAssignOp: RuleX_AssignOpContext, doc: FormattableDocument) {
        doc.surround(xAssignOp) {
            it.oneSpace()
            it.highPriority()
        }
    }

    fun format(xAtExprAt: RuleX_AtExprAtContext, doc: FormattableDocument) {
        doc.surround(xAtExprAt) {
            it.oneSpace()
            it.highPriority()
        }
    }

    fun format(xAtExprWhere: RuleX_AtExprWhereContext, doc: FormattableDocument) {
        formatBracePairWithSpace(xAtExprWhere, doc, BracePairTypes.CURLY)
        val (expressionRef, trailingComma) = xAtExprWhere.getExpressionRefWithTrailingComma()
        val lineSeparate = lineSeparateArguments(xAtExprWhere, BracePairTypes.CURLY)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(expressionRef, doc, formatAsMultiLine = lineSeparate)
    }

    fun format(xAtExprWhatCmplx: RuleX_AtExprWhatComplexContext, doc: FormattableDocument) {
        doc.prepend(xAtExprWhatCmplx) { it.oneSpace() }
        formatBracePairWithSpace(xAtExprWhatCmplx, doc, BracePairTypes.PARENTHESES)
        val formatAsMulti = lineSeparateArguments(xAtExprWhatCmplx, BracePairTypes.PARENTHESES)
        val (items, trailingComma) = xAtExprWhatCmplx.getAtExprWhatComplexItemWithTrailingComma()
        formatTrailingComma(trailingComma, doc, formatAsMulti)
        formatArguments(items, doc, formatAsMultiLine = formatAsMulti)

        items?.forEach { item ->
            val baseExpr = item.getBaseExpr()
            if (baseExpr != null && baseExpr.ruleX_BaseExprTail().isNotEmpty()) {
                val tailCall = baseExpr.ruleX_BaseExprTail().first().ruleX_BaseExprTailCall()
                if (tailCall != null) {
                    doc.prepend(tailCall) { it.oneSpace() }
                    doc.interiorIndentRangeIncludeLast(baseExpr, tailCall)
                    formatExprTailCall(
                        baseExpr.ruleX_BaseExprTail().first(),
                        null,
                        doc
                    )
                }
            }
            doc.format(item)
        }
    }

    fun format(xAtExprWhatCmplxItem: RuleX_AtExprWhatComplexItemContext, doc: FormattableDocument) {
        val itemAnnotations = xAtExprWhatCmplxItem.ruleX_Annotation()
        itemAnnotations.forEach { xAnnotation ->
            doc.append(xAnnotation) { it.oneSpace() }
            doc.format(xAnnotation.ruleX_AnnotationArgs())
        }
    }

    fun format(xVarStmt: RuleX_VarStmtContext, doc: FormattableDocument) {
        formatSemicolon(xVarStmt, doc)
        val equalSign = tokenFor(xVarStmt, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.surround(xVarStmt.ruleX_VarDeclarator()) { it.oneSpace() }
        doc.format(xVarStmt.ruleX_VarDeclarator())
        doc.format(xVarStmt.ruleX_Expression())
    }

    fun format(xTupleVarDec: RuleX_TupleVarDeclaratorContext, doc: FormattableDocument) {
        val tupleVarContext = xTupleVarDec.getTupleVarContext()
        formatSkewedOpeningClosing(tupleVarContext.ruleX_tkLPAR(), tupleVarContext, doc, BracePairTypes.PARENTHESES)
        val (varDeclarators, trailingComma) = xTupleVarDec.getVarDeclaratorWithTrailingComma()
        val lineSeparate = formatAsMultiLine(varDeclarators)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(varDeclarators, doc)
        varDeclarators?.forEach { xVarDec -> doc.format(xVarDec) }
    }

    fun format(xConstantDef: RuleX_ConstantDefContext, doc: FormattableDocument) {
        val equalSign = tokenFor(xConstantDef, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.prepend(xConstantDef.ruleX_Name()) { it.oneSpace() }
        formatType(xConstantDef, doc)
        formatSemicolon(xConstantDef, doc)
        doc.format(xConstantDef.ruleX_TypeRef())
        doc.format(xConstantDef.ruleX_ExpressionRef())
    }

    fun format(xIncrmtOp: RuleX_IncrementOperatorContext, doc: FormattableDocument) {
        doc.append(xIncrmtOp) { it.noSpace() }
    }

    fun format(xMapExpr: RuleX_NonEmptyMapLiteralExprContext, doc: FormattableDocument) {
        val mapExprContext = xMapExpr.getMapExprContext()

        formatSkewedOpeningClosing(
            mapExprContext.ruleX_tkLBRACK(),
            mapExprContext,
            doc,
            BracePairTypes.BRACKETS
        )
        val lineSeparate = lineSeparateArguments(xMapExpr, BracePairTypes.BRACKETS)
        val (mapExprEntries, trailingComma) = xMapExpr.getMapExprEntryWithTrailingComma()
        formatTrailingComma(trailingComma, doc, lineSeparate)

        mapExprEntries?.forEachIndexed { index, mapEntry ->
            doc.prepend(mapEntry) { it.oneSpace() }
            doc.append(mapEntry) { it.noSpace() }
            doc.append(mapEntry.ruleX_ExpressionRef(0)) { it.noSpace() }
            doc.prepend(mapEntry.ruleX_ExpressionRef(1)) { it.oneSpace() }

            if (lineSeparate) {
                doc.prepend(mapEntry) {
                    it.newLine()
                    it.indent()
                }
                if (index == mapExprEntries.lastIndex) {
                    doc.append(mapEntry) {
                        it.newLine()
                    }
                }
            }
            doc.format(mapEntry)
        }
    }

    fun format(xGenTypeExpr: RuleX_GenericTypeExprContext, doc: FormattableDocument) {
        doc.format(xGenTypeExpr.ruleX_GenericType())
        if (xGenTypeExpr.ruleX_BaseExprTailMember() != null) {
            doc.format(xGenTypeExpr.ruleX_BaseExprTailMember())
        } else if (xGenTypeExpr.ruleX_BaseExprTailCall() != null) {
            val tailCall = xGenTypeExpr.ruleX_BaseExprTailCall()
            val (callArg, _) = tailCall.ruleX_CallArgs().getCallArgWithTrailingComma()
            doc.prepend(tailCall) { it.noSpace() }
            if (!callArg.isNullOrEmpty()) {
                formatExprTailSingleline(tailCall, doc)
            } else {
                formatBracePairWithoutSpace(tailCall, doc, BracePairTypes.PARENTHESES)
            }
            doc.format(tailCall)
        }
    }

    fun format(xGenericType: RuleX_GenericTypeContext, doc: FormattableDocument) {
        doc.append(xGenericType.ruleX_QualifiedName()) { it.noSpace() }
        formatBracePairWithoutSpace(xGenericType.getGenericTypeContext(), doc, BracePairTypes.ANGLE)
        val (typeRef, trailingComma) = xGenericType.getTypeRefWithTrailingComma()
        val lineSeparate = formatAsMultiLine(typeRef)
        formatArguments(typeRef, doc, formatAsMultiLine = lineSeparate)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        typeRef?.forEach { xTypeRef ->
            doc.prepend(xTypeRef) { it.oneSpace() }
            doc.append(xTypeRef) { it.noSpace() }
        }
    }

    fun format(xTupleEqExpr: RuleX_TupleExprFieldContext, doc: FormattableDocument) {
        doc.surround(xTupleEqExpr.ruleX_Name()) { it.oneSpace() }
        doc.surround(xTupleEqExpr.ruleX_tkASSIGN()) { it.oneSpace() }
        doc.format(xTupleEqExpr.ruleX_ExpressionRef())
    }

    fun format(xListExpr: RuleX_ListLiteralExprContext, doc: FormattableDocument) {
        val xLisExprContext = xListExpr.getListLiteralExprContext()
        formatSkewedOpeningClosing(
            xLisExprContext.ruleX_tkLBRACK(),
            xLisExprContext,
            doc,
            BracePairTypes.BRACKETS
        )
        val (expressionRef, trailingComma) = xListExpr.getExpressionRefWithTrailingComma()
        val lineSeparate = formatAsMultiLine(expressionRef)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(expressionRef, doc)
        expressionRef?.forEach { xExprRef -> doc.format(xExprRef) }
    }

    fun format(xMirrorStruct: RuleX_MirrorStructType0Context, doc: FormattableDocument) {
        formatBracePairWithoutSpace(xMirrorStruct, doc, BracePairTypes.ANGLE)
        doc.append(xMirrorStruct) { it.noSpace() }
        doc.append(xMirrorStruct.ruleX_tkSTRUCT()) { it.noSpace() }
        doc.append(xMirrorStruct.ruleX_tkMUTABLE()) { it.oneSpace() }
    }

    fun format(xEntityDef: RuleX_EntityDefContext, doc: FormattableDocument) {
        doc.surround(xEntityDef) { it.setNewLines(2) }
        doc.interiorIndent(xEntityDef.ruleX_EntityBody())
        doc.surround(xEntityDef.ruleX_Name()) { it.oneSpace() }
        doc.format(xEntityDef.ruleX_EntityAnnotations())
        doc.format(xEntityDef.ruleX_EntityBody())
    }

    fun format(xEntityAnnotations: RuleX_EntityAnnotationsContext, doc: FormattableDocument) {
        doc.surround(xEntityAnnotations) { it.oneSpace() }
        val (xNames, trailingComma) = xEntityAnnotations.getXNamesWithTrailingComma()
        val lineSeparate = formatAsMultiLine(xNames)
        formatTrailingComma(trailingComma, doc, lineSeparate)
        formatArguments(xNames, doc, formatAsMultiLine = lineSeparate)
        formatBracePairWithoutSpace(xEntityAnnotations.getEntityAnnotationContext(), doc, BracePairTypes.PARENTHESES)
    }

    fun format(xEntitybody: RuleX_EntityBodyFullContext, doc: FormattableDocument) {
        val anyClause = xEntitybody.ruleX_RelAnyClause()
        anyClause.forEachIndexed { index, xRelAnyClause ->
            doc.prepend(xRelAnyClause) { it.newLine() }
            if (index == anyClause.lastIndex) {
                doc.append(xRelAnyClause) { it.newLine() }
            }
            doc.format(xRelAnyClause)
        }
        val closingCurly = tokenFor(xEntitybody, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }

    fun format(node: ParserRuleContext, doc: FormattableDocument) {
        try {
            val formatMethod = javaClass.getDeclaredMethod("format", node.javaClass, FormattableDocument::class.java)
            formatMethod.invoke(this, node, doc)

            return
        } catch (e: NoSuchMethodException) {
            logger.warn { "No formatting method found for: " + node.javaClass.simpleName }
        }

        for (i in 0 until node.getChildCount()) {
            val child = node.getChild(i)
            if (child is ParserRuleContext) {
                try {
                    val formatMethod =
                        javaClass.getDeclaredMethod("format", child.javaClass, FormattableDocument::class.java)
                    formatMethod.invoke(this, child, doc)
                } catch (e: NoSuchMethodException) {
                    format(child, doc)
                }
            }
        }
    }

    companion object {

        fun formatString(source: String, formatterRequest: FormatterOptions): String {
            val formatterChanges = getFormattingChanges(source, formatterRequest)
            return applyTextReplacements(source, formatterChanges)
        }

        fun getFormattingChanges(source: String, formatterRequest: FormatterOptions): List<TextReplacement> {
            val input: CharStream = CharStreams.fromString(source)
            val lexer = RellLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = RellParser(tokens)

            val formatter = RellFormatter(parser, source, formatterRequest)
            val doc = RootFormattableDocument(formatter, formatterRequest)
            formatter.format(parser.ruleX_RootParser(), doc)
            return doc.createReplacements()
        }

        fun applyTextReplacements(source: String, replacements: List<TextReplacement>): String {
            val result = StringBuilder(source)

            // Apply replacements in reverse order to avoid issues with changing offsets
            val sortedReplacements = replacements.sortedByDescending { it.startOffset }

            for (replacement in sortedReplacements) {
                if (replacement.startOffset < 0 || replacement.startOffset > source.length ||
                    replacement.stopOffset < replacement.startOffset || replacement.stopOffset > source.length
                ) {
                    // Skip invalid replacements
                    continue
                }

                result.replace(replacement.startOffset, replacement.stopOffset, replacement.text)
            }

            return result.toString()
        }
    }
}
