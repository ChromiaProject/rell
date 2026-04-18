/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter

import net.postchain.rell.toolbox.common.TextReplacement
import net.postchain.rell.toolbox.common.applyTextReplacements
import net.postchain.rell.toolbox.formatter.specialized.*
import net.postchain.rell.toolbox.formatter.util.*
import net.postchain.rell.toolbox.parser.RellCommonTokenStream
import net.postchain.rell.toolbox.parser.RellLexer
import net.postchain.rell.toolbox.parser.RellParser
import net.postchain.rell.toolbox.parser.RellParser.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.ParserRuleContext

class RellFormatter(
    val parser: RellParser,
    val source: String,
    formatterRequest: FormatterOptions
) {
    private val formatterRegistry = FormatterRegistry()
    private val visitor = FormattingVisitor(formatterRegistry)

    private val tokenAnalyzer = TokenAnalyzer(parser)
    private val braceFormatter = BraceFormatter()
    private val lineAnalyzer = LineAnalyzer(source, formatterRequest, braceFormatter)
    private val whitespaceFormatter = WhitespaceFormatter(tokenAnalyzer)
    private val argumentFormatter = ArgumentFormatter(tokenAnalyzer, whitespaceFormatter, lineAnalyzer)
    private val expressionFormatter = ExpressionFormatter(
        tokenAnalyzer,
        lineAnalyzer,
        braceFormatter,
        whitespaceFormatter,
        argumentFormatter,
    )

    init {
        registerFormatters()
    }

    private fun registerFormatters() {
        with(formatterRegistry) {
            register(RuleX_AnnotationContext::class.java, AnnotationFormatter())
            register(
                RuleX_AnnotationArgsContext::class.java,
                AnnotArgsFormatter(braceFormatter, argumentFormatter, whitespaceFormatter, lineAnalyzer)
            )

            register(RuleX_AtExprModifiers_0Context::class.java, AtExprModFormatter0(tokenAnalyzer))
            register(RuleX_AtExprModifiers_1Context::class.java, AtExprModFormatter1(tokenAnalyzer))
            register(
                RuleX_AtExprFromContext::class.java,
                AtExprFromFormatter(lineAnalyzer, braceFormatter, whitespaceFormatter, argumentFormatter)
            )
            register(RuleX_AtExprFromItemContext::class.java, AtExprFromItemFormatter(tokenAnalyzer))
            register(RuleX_AtExprAtContext::class.java, AtExprAtFormatter())
            register(
                RuleX_AtExprWhereContext::class.java,
                AtExprWhereFormatter(
                    lineAnalyzer,
                    braceFormatter,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )
            register(
                RuleX_AtExprWhatComplexContext::class.java,
                AtExprWhatCmplxFormatter(
                    lineAnalyzer,
                    braceFormatter,
                    whitespaceFormatter,
                    argumentFormatter,
                    expressionFormatter,
                )
            )
            register(RuleX_AtExprWhatComplexItemContext::class.java, AtExprWhatCmplxItemFormatter())

            register(
                RuleX_BaseExprContext::class.java,
                BaseExprFormatter(
                    expressionFormatter,
                    lineAnalyzer
                )
            )

            register(
                RuleX_NonEmptyMapLiteralExprContext::class.java,
                MapExprFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter
                )
            )
            register(RuleX_TupleExprFieldContext::class.java, TupleEqExprFormatter())
            register(
                RuleX_ListLiteralExprContext::class.java,
                ListExprFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )
            register(RuleX_MirrorStructType0Context::class.java, MirrorStructFormatter(braceFormatter))
            register(
                RuleX_TupleExprContext::class.java,
                TupleExprContextFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )

            register(RuleX_EntityDefContext::class.java, EntityDefFormatter())
            register(RuleX_EntityBodyFullContext::class.java, EntityBodyFormatter(tokenAnalyzer))
            register(
                RuleX_EntityAnnotationsContext::class.java,
                EntityAnnotationsFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer,
                )
            )
            register(RuleX_KeyIndexClauseContext::class.java, KeyIndexFormatter(whitespaceFormatter))
            register(RuleX_BaseAttributeDefinitionContext::class.java, BaseAttributeDefFormatter(tokenAnalyzer))
            register(RuleX_NameTypeAttrHeaderContext::class.java, NameTypeAttrHeadFormatter())

            register(
                RuleX_EnumDefContext::class.java,
                EnumDefFormatter(
                    lineAnalyzer,
                    whitespaceFormatter,
                    tokenAnalyzer
                )
            )

            register(
                RuleX_FunctionDefContext::class.java,
                FunctionDefFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer
                )
            )

            register(
                RuleX_FunctionBodyShortContext::class.java,
                FunctionBodyShortFormatter(whitespaceFormatter)
            )

            register(
                RuleX_GenericTypeExprContext::class.java,
                GenTypeExprFormatter(
                    braceFormatter,
                    expressionFormatter
                )
            )
            register(
                RuleX_GenericTypeContext::class.java,
                GenericTypeFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer
                )
            )

            register(
                RuleX_IfStmtContext::class.java,
                IfStmtFormatter(
                    tokenAnalyzer,
                    expressionFormatter,
                    lineAnalyzer
                )
            )
            register(RuleX_IfExprContext::class.java, IfExprFormatter(tokenAnalyzer))

            register(RuleX_NamespaceDefContext::class.java, NamespaceDefFormatter())

            register(
                RuleX_ObjectDefContext::class.java,
                ObjectDefFormatter(
                    tokenAnalyzer,
                    whitespaceFormatter
                )
            )

            register(
                RuleX_OpDefContext::class.java,
                OpDefFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer
                )
            )
            register(RuleX_IncrementOperatorContext::class.java, IncrementOpFormatter())
            register(RuleX_AssignOpContext::class.java, AssignOpFormatter())
            register(RuleX_BinaryOperatorContext::class.java, BinaryOpFormatter())
            register(RuleX_BinaryOperator_17Context::class.java, BinOp17Formatter())


            register(
                RuleX_QueryDefContext::class.java,
                QueryDefFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )

            register(RuleX_RootParserContext::class.java, RootNodeFormatter(expressionFormatter))
            register(RuleX_ModuleHeaderContext::class.java, MooduleHeaderFormatter(whitespaceFormatter))
            register(RuleX_ModifierContext::class.java, ModifierFormatter(whitespaceFormatter))

            register(RuleX_BlockStmtContext::class.java, BlockStmtFormatter())
            register(RuleX_ReturnStmtContext::class.java, ReturnStmtFormatter(whitespaceFormatter))
            register(
                RuleX_WhileStmtContext::class.java,
                WhileStmtFormatter(
                    expressionFormatter,
                    lineAnalyzer
                )
            )
            register(RuleX_ForStmtContext::class.java, ForStmtFormatter())
            register(
                RuleX_CreateExprContext::class.java,
                CreateExprFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )
            register(RuleX_DeleteStmtContext::class.java, DeleteStmtFormatter(whitespaceFormatter))

            register(
                RuleX_StructDefContext::class.java,
                StructDefFormatter(
                    tokenAnalyzer,
                    whitespaceFormatter,
                )
            )

            register(
                RuleX_UpdateStmtContext::class.java,
                UpdateStmtFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )
            register(
                RuleX_UpdateTargetAtContext::class.java,
                UpdateTargetAtFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )

            register(RuleX_WhenStmtContext::class.java, WhenStmtFormatter(tokenAnalyzer))
            register(RuleX_WhenExprContext::class.java, WhenExprFormatter(tokenAnalyzer))
            register(RuleX_WhenExprCaseContext::class.java, WhenCaseFormatter())
            register(RuleX_WhenConditionExprContext::class.java, WhenCondExprFormatter())

            register(
                RuleX_VarStmtContext::class.java,
                VarStmtFormatter(
                    whitespaceFormatter,
                    tokenAnalyzer
                )
            )
            register(
                RuleX_TupleVarDeclaratorContext::class.java,
                TupleVarDecFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer
                )
            )
            register(
                RuleX_ConstantDefContext::class.java,
                ConstantDefFormatter(
                    whitespaceFormatter,
                    tokenAnalyzer
                )
            )
        }
    }

    fun format(node: ParserRuleContext, doc: FormattableDocument) {
        visitor.visit(node, doc)
    }

    companion object {

        fun formatString(source: String, formatterRequest: FormatterOptions): String {
            val formatterChanges = getFormattingChanges(source, formatterRequest)
            return applyTextReplacements(source, formatterChanges)
        }

        fun getFormattingChanges(source: String, formatterRequest: FormatterOptions): List<TextReplacement> {
            val input: CharStream = CharStreams.fromString(source)
            val lexer = RellLexer(input)
            val tokenStream = RellCommonTokenStream(lexer)
            val parser = RellParser(tokenStream)
            val rootNode = parser.ruleX_RootParser()

            val formatter = RellFormatter(parser, source, formatterRequest)
            val tokenAnalyzer = TokenAnalyzer(parser)
            val doc = RootFormattableDocument(formatter, tokenAnalyzer, formatterRequest)
            doc.formatRellDocsComments(tokenStream)
            formatter.format(rootNode, doc)
            return doc.createReplacements()
        }
    }
}
