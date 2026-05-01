/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

@file:Suppress("all")
@file:JvmName("AntlrGrammarGenerator")
package net.postchain.rell.lsp.grammar

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.ParserReference
import net.postchain.rell.base.compiler.parser.RellToken
import net.postchain.rell.base.compiler.parser.RellTokens
import net.postchain.rell.base.utils.LateInit
import net.postchain.rell.base.utils.capitalizeEx
import net.postchain.rell.base.utils.grammar.GrammarUtils
import org.apache.commons.collections4.MapUtils
import java.util.*

// TODO: Grammar generator should be revisited as current implementation was not used for initial generation.
// It's used by action generator for tranformer generation.

fun main() {
    generateHeader()
    generateNonterminals()
    generateFooter()
}

private val RELL_TOKENS = RellTokens.DEFAULT
private val TOP_PARSERS = GrammarUtils.getParsers()

private fun generateHeader() {
    val tokens = RELL_TOKENS

    val text = """
        {
          parserClass="net.postchain.rellide.jetbrains.language.parser.RellParser"
          parserUtilClass="net.postchain.rellide.jetbrains.language.parser.RellParserUtil"

          extends="com.intellij.extapi.psi.ASTWrapperPsiElement"

          psiClassPrefix="Rell"
          psiImplClassSuffix="Impl"
          psiPackage="net.postchain.rellide.jetbrains.language.psi"
          psiImplPackage="net.postchain.rellide.jetbrains.language.psi.impl"

          elementTypeHolderClass="net.postchain.rellide.jetbrains.language.psi.RellTypes"
          elementTypeClass="net.postchain.rellide.jetbrains.language.psi.RellElementType"
          tokenTypeClass="net.postchain.rellide.jetbrains.language.psi.RellTokenType"

          psiImplUtilClass="net.postchain.rellide.jetbrains.language.psi.impl.RellPsiImplUtil"

          tokens=[
            space='regexp:\s+'
            booleanLiteral='regexp:true|false'

            SL_COMMENT="regexp://.*"
            ML_COMMENT="regexp:/\*([^*]|[\r\n]|(\*+([^*/]|[\r\n])))*\*+/"

            WS="regexp:[ \t\r\n]+"
            ${tokens.identifier.name}='regexp:[a-zA-Z_][a-zA-Z_0-9]*'
            DECNUM="regexp:[0-9]+"
            HEXDIGNUM="regexp:0\s*x\s*[0-9A-Fa-f]+"
            DECIMAL="regexp:[0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?"
            HEXDIG="regexp:[0-9A-Fa-f]"
            ${tokens.byteArray.name}="regexp:x(('([_0-9a-fA-F][_0-9a-fA-F])*')|(\"([_0-9a-fA-F][_0-9a-fA-F])*\"))"
            STRBAD="regexp:\\|'\u0000' .. '\u001F'"
            STRING_NOT_CLOSED="regexp:(\"(\t|\\[btnfr\"\'\\]|\\u[0-9A-Fa-f]{4}|[^\"\\\u0000-\u001F])*)|('(\t|\\[btnfr\"\'\\]|\\u[0-9A-Fa-f]{4}|[^\'\\\u0000-\u001F])*)"
            ${tokens.string.name}="regexp:(\"(\t|\\[btnfr\"\'\\]|\\u[0-9A-Fa-f]{4}|[^\"\\\u0000-\u001F])*\")|('(\t|\\[btnfr\"\'\\]|\\u[0-9A-Fa-f]{4}|[^\'\\\u0000-\u001F])*\')"
          ]
        }
    """.trimIndent()

    println(text.trim())
}

private fun generateFooter() {
    val tokens = RELL_TOKENS
    val text = """
        COMMON_INT ::= HEXDIGNUM | DECNUM;
        ${tokens.bigInteger.name} ::= COMMON_INT 'L';
        ${tokens.integer.name} ::= COMMON_INT;

        STRCHAR ::= '\t' | '\\' ('b'|'t'|'n'|'f'|'r'|'"'|"'"|'\\' | 'u' HEXDIG HEXDIG HEXDIG HEXDIG)

        X_tkElse ::= 'else'
        X_tkLimit ::= 'limit'
        X_tkOffset ::= 'offset'
        X_tkArrow ::= '->'
        X_tkRPAR ::= ')'
        X_tkRCURL ::= '}'
        X_tkRBRACK ::= ']'
    """.trimIndent()

    println(text.trim())
}

private fun generateTerminals() {
    val tokens = RELL_TOKENS

    val text = """
            terminal ML_COMMENT: '/*' -> '*/';
            terminal SL_COMMENT: '//' !('\n'|'\r')* ('\r'? '\n')?;
            terminal WS: (' '|'\t'|'\r'|'\n')+;

            terminal ${tokens.identifier.name}: ('A'..'Z'|'a'..'z'|'_') ('A'..'Z'|'a'..'z'|'_'|'0'..'9')*;

            terminal DECNUM: ('0'..'9')+;
            terminal EXPONENT: ('E'|'e') ('+'|'-')? DECNUM ;
            terminal ${tokens.decimal.name}: DECNUM? '.' DECNUM EXPONENT? | DECNUM EXPONENT ;

            terminal HEXDIG: '0'..'9'|'A'..'F'|'a'..'f';
            terminal COMMON_INT: DECNUM | '0' 'x' HEXDIG+;
            terminal ${tokens.bigInteger.name}: COMMON_INT 'L';
            terminal ${tokens.integer.name}: COMMON_INT;

            terminal ${tokens.byteArray.name}: 'x' (('\'' (HEXDIG HEXDIG)* '\'') | ('"' (HEXDIG HEXDIG)* '"'));

            terminal STRCHAR: '\t' | '\\' ('b'|'t'|'n'|'f'|'r'|'"'|"'"|'\\' | 'u' HEXDIG HEXDIG HEXDIG HEXDIG);
            terminal STRBAD: '\\' | '\u0000' .. '\u001F';
            terminal ${tokens.string.name}: '"' ( STRCHAR | !('"'|STRBAD) )*  '"' | "'" ( STRCHAR | !("'"|STRBAD) )* "'";
    """.trimIndent()

    println(text.trim())
}

private fun generateNonterminals() {
    val nonterms = AntlrNontermGen.generateNonterms()
    for (nt in nonterms) {
        println(nt.generate())
    }
}

fun generateAntlrActions(): Map<String, AntlrActionEx> {
    val actions = AntlrNontermGen.generateActions()
    return actions
}

private object AntlrNontermGen {
    private val literalTokens = (RELL_TOKENS.keywords + RELL_TOKENS.delims).associateBy { it.name }.toMap()
    private val specialTokens = listOf(RELL_TOKENS.string, RELL_TOKENS.byteArray).map { it.name }

    private val kParsers = TOP_PARSERS

    private val xNonterms = mutableMapOf<String, AntlrNonterm>()
    private val xTokenNonterms = mutableMapOf<String, AntlrNonterm>()

    private val actions = mutableMapOf<String, AntlrActionEx>()

    fun generateNonterms(): List<AntlrNonterm> {
        generate()
        return xNonterms.values.toList()
    }

    fun generateActions(): Map<String, AntlrActionEx> {
        generate()
        return actions.toMap()
    }

    private fun generate() {
        convertNonterm("rootParser")
    }

    private fun convertNonterm(name: String): AntlrExpr {
        val xName = nontermNameToAntlr(name)

        if (name !in xNonterms) {
            val parser = kParsers.getValue(name)
            val gram = AntlrGramExprGen.createAntlrGramExpr(parser)

            val xNt = AntlrNonterm(xName)
            xNonterms[name] = xNt
            xNt.prods = convertProds(xName, gram)
        }

        return AntlrExpr_Symbol(xName)
    }

    private fun convertProds(xNonterm: String, gram: AntlrGramExpr): List<AntlrProd> {
        val subs = if (gram is AntlrGramExpr_Or) gram.subs else listOf(gram)
        return subs.mapIndexed { i, sub -> convertProd(xNonterm, sub, i, subs.size) }
    }

    private fun convertProd(xNonterm: String, gram: AntlrGramExpr, index: Int, count: Int): AntlrProd {
        val type = if (count == 1) xNonterm else "${xNonterm}_$index"

        val (inner, transform) = if (gram is AntlrGramExpr_Map) {
            Pair(gram.sub, gram.transform)
        } else {
            Pair(gram, null)
        }

        val subs = if (inner is AntlrGramExpr_And) inner.subs else listOf(inner)
        if (subs.size == 1) {
            val sub = subs[0]
            if (sub is AntlrGramExpr_Nonterm && transform == null) {
                val expr = convertExpr(sub, null)
                return AntlrProd(expr)
            } else if (sub is AntlrGramExpr_Token) {
                val expr = convertExpr(sub, null)
                if (transform == null) {
                    createTokenType(sub.name)
                    return AntlrProd(expr)
                } else {
                    val token = if (sub.name in specialTokens) sub.name else null
                    createAction(type, AntlrAction_Token(token), transform)
                    return AntlrProd(expr)
                }
            }
        }

        val exprs = mutableListOf<AntlrExpr>()
        val attrs = mutableListOf<AntlrAttr>()
        for (sub in subs) {
            var attr: AntlrAttr? = null
            if (sub.hasValue()) {
                val attrName = sub.getRuleClassString()
                attr = AntlrAttr(attrName, sub.many())
                attrs.add(attr)
            }
            val expr = convertExpr(sub, attr)
            exprs.add(expr)
        }

        val expr = if (exprs.size == 1) exprs[0] else AntlrExpr_And(exprs)
        createAction(type, AntlrAction_General(attrs), transform)

        return AntlrProd(expr)
    }

    private fun convertExpr(gram: AntlrGramExpr, attr: AntlrAttr?): AntlrExpr {
        return when (gram) {
            is AntlrGramExpr_Token -> convertToken(gram.name, attr)
            is AntlrGramExpr_Nonterm -> createAttr(convertNonterm(gram.name), attr)
            is AntlrGramExpr_Skip -> convertExpr(gram.sub, null)
            is AntlrGramExpr_And -> {
                if (attr != null) {
                    val values = gram.subs.filter { it.hasValue() }
                    check(values.size <= 1) { "More than one element has value" }
                }
                AntlrExpr_And(gram.subs.map { convertExpr(it, if (it.hasValue()) attr else null) })
            }
            is AntlrGramExpr_Or -> AntlrExpr_Or(gram.subs.map { convertExpr(it, attr) })
            is AntlrGramExpr_Opt -> AntlrExpr_Opt(convertExpr(gram.sub, attr))
            is AntlrGramExpr_Rep -> {
                val term = convertExpr(gram.term, attr)
                if (gram.sep == null) {
                    AntlrExpr_Rep(term, gram.zero)
                } else {
                    val sep = convertExpr(gram.sep, null)
                    val rep = AntlrExpr_Rep(AntlrExpr_And(listOf(sep, term)), true)
                    val one = AntlrExpr_And(listOf(term, rep))
                    if (gram.zero) AntlrExpr_Opt(one) else one
                }
            }
            is AntlrGramExpr_Map -> error("Map not expected here")
        }
    }

    private fun convertToken(name: String, attr: AntlrAttr?): AntlrExpr {
        if (attr == null) {
            return convertToken0(name)
        }

        if (name !in xTokenNonterms) {
            val ntName = termNameToAntlr("tk$name")
            check(ntName !in xNonterms)
            val expr = convertToken0(name)
            val type = createTokenType(name)
            val prod = AntlrProd(expr)
            val nonterm = AntlrNonterm(ntName)
            nonterm.prods = listOf(prod)
            xTokenNonterms[name] = nonterm
            xNonterms[ntName] = nonterm
        }

        val nonterm = xTokenNonterms.getValue(name)
        val expr = AntlrExpr_Symbol(nonterm.name)
        return createAttr(expr, attr)
    }

    private fun convertToken0(name: String): AntlrExpr {
        val token = literalTokens[name]
        return if (token != null) AntlrExpr_Token(token.pattern) else AntlrExpr_Symbol(name)
    }

    private fun createTokenType(name: String): String {
        val tail = if (name !in specialTokens) "" else name.lowercase(Locale.getDefault()).capitalizeEx()
        val type = nontermNameToAntlr("token$tail")
        if (type !in actions) {
            val token = if (name in specialTokens) name else null
            actions[type] = AntlrActionEx(AntlrAction_Token(token), null)
        }
        return type
    }

    private fun createAttr(expr: AntlrExpr, attr: AntlrAttr?): AntlrExpr {
        return if (attr == null) expr else AntlrExpr_Attr(attr.name, attr.many, expr)
    }

    private fun createAction(type: String, action: AntlrAction, transform: ((Any) -> Any)?) {
        check(type !in actions) { type }
        actions[type] = AntlrActionEx(action, transform)
    }
}

private object AntlrGramExprGen {
    private val parsers = GrammarUtils.getParsers()
    private val nonterms = MapUtils.invertMap(parsers).toMap()

    fun createAntlrGramExpr(parser: Any): AntlrGramExpr {
        return createAntlrGramExpr0(parser)
    }

    private fun createAntlrGramExprSub(parser: Any): AntlrGramExpr {
        val nt = nonterms[parser]
        if (nt != null) {
            return AntlrGramExpr_Nonterm(nt)
        }
        return createAntlrGramExpr0(parser)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createAntlrGramExpr0(parser: Any): AntlrGramExpr {
        return when (parser) {
            is ParserReference<*> -> createAntlrGramExprSub(parser.parser)
            is RellToken -> AntlrGramExpr_Token(parser.name)
            is SkipParser -> AntlrGramExpr_Skip(createAntlrGramExprSub(parser.innerParser))
            is AndCombinator<*> -> AntlrGramExpr_And(GrammarUtils.andParsers(parser).map { createAntlrGramExprSub(it) })
            is OrCombinator<*> -> AntlrGramExpr_Or(GrammarUtils.orParsers(parser).map { createAntlrGramExprSub(it) })
            is OptionalCombinator<*> -> AntlrGramExpr_Opt(createAntlrGramExprSub(parser.parser))
            is SeparatedCombinator<*, *> -> {
                val term = createAntlrGramExprSub(parser.termParser)
                val sep = createAntlrGramExprSub(parser.separatorParser)
                AntlrGramExpr_Rep(term, sep, parser.acceptZero)
            }
            is RepeatCombinator<*> -> {
                check(parser.atLeast >= 0)
                check(parser.atMost == -1)
                AntlrGramExpr_Rep(createAntlrGramExprSub(parser.parser), null, parser.atLeast == 0)
            }
            is MapCombinator<*, *> -> {
                if (parser.innerParser is SeparatedCombinator<*, *>) {
                    createAntlrGramExprSub(parser.innerParser)
                } else {
                    AntlrGramExpr_Map(createAntlrGramExprSub(parser.innerParser), parser.transform as (Any) -> Any)
                }
            }
            else -> error(parser::class.java.simpleName)
        }
    }
}

private fun nontermNameToAntlr(name: String): String = "X_" + name.capitalizeEx()

private fun nontermNameToAntlrRuleCtx(name: String): String {
    if (name.all { it.isUpperCase() }) {
        return "RuleX_tk" + name.capitalizeEx() + "Context"
    }
    return "RuleX_" + name.capitalizeEx() + "Context"
}

private fun termNameToAntlr(name: String): String = "X_$name"

private class AntlrNonterm(val name: String) {
    var prods by LateInit<List<AntlrProd>>()

    fun generate(): String {
        val ps = prods.joinToString("\n   | ") { it.generate() }
        if (name == "X_IfStmt") {
            return "$name ::= ${ps.replace("'else'", "X_tkElse")}\n"
        }
        if (name in listOf("X_LiteralExpr", "X_BaseExprHead")) {
            // Swap order or X_IntExpr and X_BigIntExpr
            val regex = Regex("(X_IntExpr)(\\s+\\|\\s+)(X_BigIntExpr)", RegexOption.MULTILINE)
            return "$name ::= ${ps.replace(regex, "$3$2$1")}\n"
        }
        return "\n$name ::= $ps\n"
    }
}

private class AntlrProd(private val expr: AntlrExpr) {
    fun generate(): String = expr.generate()
}

private sealed class AntlrExpr {
    abstract fun generate(): String
}

private class AntlrExpr_Symbol(private val name: String) : AntlrExpr() {
    override fun generate() = name
}

private class AntlrExpr_Token(private val text: String) : AntlrExpr() {
    override fun generate() = "'$text'"
}

private class AntlrExpr_Or(private val subs: List<AntlrExpr>) : AntlrExpr() {
    override fun generate() = "(" + subs.joinToString(" | ") { it.generate() } + ")"
}

private class AntlrExpr_And(private val subs: List<AntlrExpr>) : AntlrExpr() {
    override fun generate() = subs.joinToString(" ") { it.generate() }
}

private class AntlrExpr_Rep(private val sub: AntlrExpr, private val zero: Boolean) : AntlrExpr() {
    override fun generate() = "(" + sub.generate() + ")" + (if (zero) "*" else "+")
}

private class AntlrExpr_Opt(private val sub: AntlrExpr) : AntlrExpr() {
    override fun generate() = "(" + sub.generate() + ")?"
}

private class AntlrExpr_Attr(private val attr: String, private val many: Boolean, private val sub: AntlrExpr) : AntlrExpr() {
    override fun generate(): String {
        val op = if (many) "+=" else "="
        return sub.generate()
    }
}

private sealed class AntlrGramExpr {
    abstract fun hasValue(): Boolean
    abstract fun many(): Boolean
    abstract fun getRuleClassString(): String
}

private class AntlrGramExpr_Token(val name: String) : AntlrGramExpr() {
    override fun hasValue() = true
    override fun many() = false
    override fun getRuleClassString(): String = nontermNameToAntlrRuleCtx(name)
}

private class AntlrGramExpr_Nonterm(val name: String) : AntlrGramExpr() {
    override fun hasValue() = true
    override fun many() = false

    override fun getRuleClassString(): String = nontermNameToAntlrRuleCtx(name)
}

private class AntlrGramExpr_Skip(val sub: AntlrGramExpr) : AntlrGramExpr() {
    override fun hasValue() = false
    override fun many() = sub.many()

    override fun getRuleClassString(): String = sub.getRuleClassString()
}

private class AntlrGramExpr_Map(val sub: AntlrGramExpr, val transform: (Any) -> Any) : AntlrGramExpr() {
    override fun hasValue() = true
    override fun many() = false

    override fun getRuleClassString(): String = sub.getRuleClassString()
}

private class AntlrGramExpr_And(val subs: List<AntlrGramExpr>) : AntlrGramExpr() {
    override fun hasValue() = subs.any { it.hasValue() }
    override fun many() = subs.any { it.many() }
    override fun getRuleClassString(): String = subs[0].getRuleClassString()
}

private class AntlrGramExpr_Or(val subs: List<AntlrGramExpr>) : AntlrGramExpr() {
    override fun hasValue() = subs.any { it.hasValue() }
    override fun many() = subs.any { it.many() }

    override fun getRuleClassString(): String = subs[0].getRuleClassString()
}

private class AntlrGramExpr_Opt(val sub: AntlrGramExpr) : AntlrGramExpr() {
    override fun hasValue() = sub.hasValue()
    override fun many() = sub.many()

    override fun getRuleClassString(): String = sub.getRuleClassString()
}

private class AntlrGramExpr_Rep(val term: AntlrGramExpr, val sep: AntlrGramExpr?, val zero: Boolean) : AntlrGramExpr() {
    override fun hasValue() = term.hasValue()
    override fun many() = true
    override fun getRuleClassString(): String = term.getRuleClassString()
}
