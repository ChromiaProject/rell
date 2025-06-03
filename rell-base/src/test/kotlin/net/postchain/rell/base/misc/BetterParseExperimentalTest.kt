/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.misc

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import org.junit.Test
import kotlin.test.assertEquals

class BetterParseExperimentalTest {
    @Test fun test() {
        assertEquals("name[foo]", TestGrammar.parseToEnd("foo;"))
        assertEquals("select[bar]", TestGrammar.parseToEnd("bar@;"))
    }

    @Test fun testTupleAmbiguity() {
        assertEquals("name[foo]", TupleGrammar.parseToEnd("foo"))
        assertEquals("num[123]", TupleGrammar.parseToEnd("123"))
        assertEquals("num[123]", TupleGrammar.parseToEnd("(123)"))
        assertEquals("tuple[(null,num[123])]", TupleGrammar.parseToEnd("(123,)"))
        assertEquals("name[foo]", TupleGrammar.parseToEnd("(foo)"))
        assertEquals("tuple[(null,name[foo])]", TupleGrammar.parseToEnd("(foo,)"))
        assertEquals("binary[name[foo],[(EQ,num[123])]]", TupleGrammar.parseToEnd("foo=123"))
        assertEquals("binary[name[foo],[(EQ,num[123])]]", TupleGrammar.parseToEnd("(foo=123)"))
        assertEquals("tuple[(foo,num[123])]", TupleGrammar.parseToEnd("(foo=123,)"))
        assertEquals("tuple[(foo,num[123]),(bar,num[456])]", TupleGrammar.parseToEnd("(foo=123,bar=456)"))
        assertEquals("tuple[(foo,num[123]),(bar,num[456])]", TupleGrammar.parseToEnd("(foo=123,bar=456,)"))
        assertEquals("tuple[(foo,num[123]),(bar,num[456]),(baz,num[789])]", TupleGrammar.parseToEnd("(foo=123,bar=456,baz=789)"))
        assertEquals("tuple[(foo,num[123]),(bar,num[456]),(baz,num[789])]", TupleGrammar.parseToEnd("(foo=123,bar=456,baz=789,)"))
    }

    private object TestGrammar: Grammar<String>() {
        private val ID by regexToken("[A-ZA-z][A-Za-z0-9]*")
        private val AT by literalToken("@")
        private val SEMI by literalToken(";")

        private val nameExpr by ( ID ) map { "name[${it.text}]" }
        private val selectExpr by ( ID * -AT ) map { id -> "select[${id.text}]" }
        private val expr by ( selectExpr or nameExpr )

        private val stmt by ( expr * -SEMI )

        override val rootParser by stmt
    }

    private object TupleGrammar: Grammar<String>() {
        private val ID by regexToken("[A-ZA-z][A-Za-z0-9]*")
        private val NUM by regexToken("[0-9]+")
        private val LPAR by literalToken("(")
        private val RPAR by literalToken(")")
        private val COMMA by literalToken(",")
        private val COLON by literalToken(":")
        private val EQ by literalToken("=")
        private val NE by literalToken("!=")

        private val _expr by parser(this::expr)

        private val nameExpr by (ID) map { "name[${it.text}]" }
        private val numExpr by (NUM) map { "num[${it.text}]" }

        private val parExpr by ( -LPAR * _expr * -RPAR) map { it }

        private val tupField by ( optional(ID * -EQ) * _expr) map { ( name, expr ) -> Pair(name?.text, expr) }

        private val tupTail by ( separatedTerms(tupField, COMMA, true) * -optional(COMMA) )

        private val tupExpr by ( -LPAR * tupField * -COMMA * optional(tupTail) * -RPAR) map {
            (field, tail) ->
            if (tail == null && field.first == null) {
                field.second
            } else {
                val fields = (listOf(field) + tail.orEmpty()).joinToString(",") { (a, b) -> "($a,$b)" }
                "tuple[$fields]"
            }
        }

        private val operandExpr by ( nameExpr or numExpr or parExpr or tupExpr )

        private val binaryOperator = ( EQ or NE )

        private val binaryExprOperand by ( binaryOperator * operandExpr ) map { ( op, expr ) -> "(${op.type.name},$expr)" }

        private val binaryExpr by ( operandExpr * zeroOrMore(binaryExprOperand) ) map { ( head, tail ) ->
            if (tail.isEmpty()) head else "binary[$head,$tail]"
        }

        private val expr: Parser<String> by binaryExpr

        override val rootParser by expr
    }
}
