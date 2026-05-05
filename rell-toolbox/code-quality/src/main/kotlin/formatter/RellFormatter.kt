/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter

import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.base.compiler.parser.antlr.RellParser.*
import net.postchain.rell.toolbox.common.TextReplacement
import net.postchain.rell.toolbox.common.applyTextReplacements
import net.postchain.rell.toolbox.formatter.specialized.*
import net.postchain.rell.toolbox.formatter.util.*
import net.postchain.rell.toolbox.parser.RellCommonTokenStream
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
            register(AnnotationContext::class.java, AnnotationFormatter(whitespaceFormatter))
            register(
                AnnotationArgsContext::class.java,
                AnnotArgsFormatter(braceFormatter, argumentFormatter, whitespaceFormatter, lineAnalyzer)
            )

            register(
                AtExprAtContext::class.java, AtExprAtFormatter()
            )
            register(
                AtExprWhereContext::class.java,
                AtExprWhereFormatter(
                    lineAnalyzer,
                    braceFormatter,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )
            register(
                AtExprWhatComplexContext::class.java,
                AtExprWhatCmplxFormatter(
                    lineAnalyzer,
                    braceFormatter,
                    whitespaceFormatter,
                    argumentFormatter,
                    expressionFormatter,
                )
            )
            register(AtExprModifiersContext::class.java, AtExprModFormatter(tokenAnalyzer))

            register(
                BaseExprContext::class.java,
                BaseExprFormatter(
                    expressionFormatter,
                    lineAnalyzer
                )
            )
            register(ExpressionContext::class.java, ExpressionInlineOpFormatter())

            register(
                NonEmptyMapLiteralExprContext::class.java,
                MapExprFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter
                )
            )
            register(
                ListLiteralExprContext::class.java,
                ListExprFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )
            register(
                MirrorStructExprContext::class.java,
                MirrorStructExprFormatterImpl(braceFormatter)
            )
            register(
                MirrorStructTypeContext::class.java,
                MirrorStructTypeFormatter(braceFormatter)
            )
            register(
                TupleHeadContext::class.java,
                TupleHeadFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )
            register(
                AtExprContext::class.java,
                AtExprFromFormatter(
                    lineAnalyzer,
                    braceFormatter,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )

            register(EntityDefContext::class.java, EntityDefFormatter())
            register(EntityBodyContext::class.java, EntityBodyFormatter(tokenAnalyzer))
            register(
                EntityAnnotationsContext::class.java,
                EntityAnnotationsFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer,
                )
            )
            register(KeyIndexClauseContext::class.java, KeyIndexFormatter(whitespaceFormatter))
            register(BaseAttributeDefinitionContext::class.java, BaseAttributeDefFormatter(tokenAnalyzer))
            register(NameTypeAttrHeaderContext::class.java, NameTypeAttrHeadFormatter())

            register(
                EnumDefContext::class.java,
                EnumDefFormatter(
                    lineAnalyzer,
                    whitespaceFormatter,
                    tokenAnalyzer
                )
            )

            register(
                FunctionDefContext::class.java,
                FunctionDefFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer
                )
            )

            register(
                FunctionBodyContext::class.java,
                FunctionBodyFormatter(whitespaceFormatter)
            )
            register(
                QueryBodyContext::class.java,
                QueryBodyFormatter(whitespaceFormatter)
            )

            register(
                GenericTypeExprContext::class.java,
                GenTypeExprFormatter(
                    braceFormatter,
                    expressionFormatter
                )
            )
            register(
                GenericOrNameTypeContext::class.java,
                GenericTypeFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer
                )
            )

            register(
                IfStmtAltContext::class.java,
                IfStmtFormatter(
                    tokenAnalyzer,
                    expressionFormatter,
                    lineAnalyzer
                )
            )
            register(IfExprContext::class.java, IfExprFormatter(tokenAnalyzer))

            register(NamespaceDefContext::class.java, NamespaceDefFormatter(tokenAnalyzer))

            register(
                ObjectDefContext::class.java,
                ObjectDefFormatter(
                    tokenAnalyzer,
                    whitespaceFormatter
                )
            )

            register(
                OpDefContext::class.java,
                OpDefFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer
                )
            )

            register(
                QueryDefContext::class.java,
                QueryDefFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )

            register(FileContext::class.java, RootNodeFormatter(expressionFormatter, tokenAnalyzer))
            register(ModuleHeaderContext::class.java, MooduleHeaderFormatter(whitespaceFormatter))
            register(ModifierContext::class.java, ModifierFormatter(whitespaceFormatter))

            register(BlockStmtContext::class.java, BlockStmtFormatter())
            register(ReturnStmtAltContext::class.java, ReturnStmtFormatter(whitespaceFormatter, tokenAnalyzer))
            register(
                WhileStmtAltContext::class.java,
                WhileStmtFormatter(
                    expressionFormatter,
                    lineAnalyzer,
                    tokenAnalyzer
                )
            )
            register(ForStmtAltContext::class.java, ForStmtFormatter(tokenAnalyzer))
            register(
                CreateExprContext::class.java,
                CreateExprFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter,
                    tokenAnalyzer,
                    expressionFormatter,
                )
            )
            register(DeleteStmtAltContext::class.java, DeleteStmtFormatter(whitespaceFormatter, tokenAnalyzer))

            register(
                StructDefContext::class.java,
                StructDefFormatter(
                    tokenAnalyzer,
                    whitespaceFormatter,
                )
            )

            register(
                UpdateStmtAltContext::class.java,
                UpdateStmtFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter,
                    tokenAnalyzer
                )
            )
            register(
                UpdateTargetAtContext::class.java,
                UpdateTargetAtFormatter(
                    braceFormatter,
                    lineAnalyzer,
                    whitespaceFormatter,
                    argumentFormatter
                )
            )

            register(WhenStmtAltContext::class.java, WhenStmtFormatter(tokenAnalyzer))
            register(WhenExprContext::class.java, WhenExprFormatter(tokenAnalyzer))
            register(WhenConditionExprContext::class.java, WhenCondExprFormatter())

            register(
                VarStmtAltContext::class.java,
                VarStmtFormatter(
                    whitespaceFormatter,
                    tokenAnalyzer
                )
            )
            register(
                TupleVarDeclaratorContext::class.java,
                TupleVarDecFormatter(
                    braceFormatter,
                    argumentFormatter,
                    whitespaceFormatter,
                    lineAnalyzer
                )
            )
            register(
                ConstantDefContext::class.java,
                ConstantDefFormatter(
                    whitespaceFormatter,
                    tokenAnalyzer
                )
            )
            register(
                ExprStmtAltContext::class.java,
                ExprStmtAltFormatter(whitespaceFormatter, tokenAnalyzer)
            )
            register(
                IncrementStmtAltContext::class.java,
                IncrementStmtAltFormatter(whitespaceFormatter)
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
            val rootNode = parser.file()

            val formatter = RellFormatter(parser, source, formatterRequest)
            val tokenAnalyzer = TokenAnalyzer(parser)
            val doc = RootFormattableDocument(formatter, tokenAnalyzer, formatterRequest)
            doc.formatRellDocsComments(tokenStream)
            formatter.format(rootNode, doc)
            return doc.createReplacements()
        }
    }
}
