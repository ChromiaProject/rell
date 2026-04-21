/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.grammar

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.ParserReference
import net.postchain.rell.base.compiler.parser.RellToken
import net.postchain.rell.base.compiler.parser.RellTokens
import net.postchain.rell.base.utils.*

fun main() {
    XtextGenUtils.printHeader()

    println("grammar net.postchain.rellide.xtext.Rell hidden(WS, ML_COMMENT, SL_COMMENT)\n")
    println("import 'http://www.eclipse.org/emf/2002/Ecore' as ecore\n")
    println("generate rell 'http://www.postchain.net/Rell'")

    generateNonterminals()
    println()

    generateTerminals()
}

private val RELL_TOKENS = RellTokens.DEFAULT
private val TOP_PARSERS = GrammarUtils.getParsers()

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
    val nonterms = XtextNontermGen.generateNonterms()
    for (nt in nonterms) {
        println(nt.generate())
    }
}

internal fun generateXtextActions(): Map<String, XtextActionEx> {
    val actions = XtextNontermGen.generateActions()
    return actions
}

private object XtextNontermGen {
    private val literalTokens = (RELL_TOKENS.keywords + RELL_TOKENS.delims).associateBy { it.name }.toMap()
    private val specialTokens = listOf(RELL_TOKENS.string, RELL_TOKENS.byteArray).map { it.name }

    private val kParsers = TOP_PARSERS

    private val xNonterms = mutableMapOf<String, XtextNonterm>()
    private val xTokenNonterms = mutableMapOf<String, XtextNonterm>()

    private val actions = mutableMapOf<String, XtextActionEx>()

    fun generateNonterms(): List<XtextNonterm> {
        generate()
        return xNonterms.values.toList()
    }

    fun generateActions(): Map<String, XtextActionEx> {
        generate()
        return actions.toMap()
    }

    private fun generate() {
        convertNonterm("rootParser")
    }

    private fun convertNonterm(name: String): XtextExpr {
        val xName = nontermNameToXtext(name)

        if (name !in xNonterms) {
            val parser = kParsers.getValue(name)
            val gram = GramExprGen.createGramExpr(parser)

            val xNt = XtextNonterm(xName)
            xNonterms[name] = xNt
            xNt.prods = convertProds(xName, gram)
        }

        return XtextExpr_Symbol(xName)
    }

    private fun convertProds(xNonterm: String, gram: GramExpr): List<XtextProd> {
        val subs = if (gram is GramExpr_Or) gram.subs else listOf(gram)
        return subs.mapIndexed { i, sub -> convertProd(xNonterm, sub, i, subs.size) }
    }

    private fun convertProd(xNonterm: String, gram: GramExpr, index: Int, count: Int): XtextProd {
        val type = if (count == 1) xNonterm else "${xNonterm}_$index"

        val (inner, transform) = if (gram is GramExpr_Map) {
            Pair(gram.sub, gram.transform)
        } else {
            Pair(gram, null)
        }

        val subs = if (inner is GramExpr_And) inner.subs else listOf(inner)
        if (subs.size == 1) {
            val sub = subs[0]
            if (sub is GramExpr_Nonterm && transform == null) {
                val expr = convertExpr(sub, null)
                return XtextProd(null, expr)
            } else if (sub is GramExpr_Token) {
                val expr = convertExpr(sub, null)
                return if (transform == null) {
                    val tokenType = createTokenType(sub.name)
                    XtextProd(tokenType, expr)
                } else {
                    val token = if (sub.name in specialTokens) sub.name else null
                    createAction(type, XtextAction_Token(token), transform)
                    XtextProd(type, expr)
                }
            }
        }

        val exprs = mutableListOf<XtextExpr>()
        val attrs = mutableListOf<XtextAttr>()
        for (sub in subs) {
            var attr: XtextAttr? = null
            if (sub.hasValue()) {
                val attrName = "" + Character.forDigit(10 + attrs.size, 36)
                attr = XtextAttr(attrName, sub.many())
                attrs.add(attr)
            }
            val expr = convertExpr(sub, attr)
            exprs.add(expr)
        }

        val expr = if (exprs.size == 1) exprs[0] else XtextExpr_And(exprs.toImmList())
        createAction(type, XtextAction_General(attrs.toImmList()), transform)

        return XtextProd(type, expr)
    }

    private fun convertExpr(gram: GramExpr, attr: XtextAttr?): XtextExpr = when (gram) {
        is GramExpr_Token -> convertToken(gram.name, attr)
        is GramExpr_Nonterm -> createAttr(convertNonterm(gram.name), attr)
        is GramExpr_Skip -> convertExpr(gram.sub, null)
        is GramExpr_And -> {
            if (attr != null) {
                val values = gram.subs.filter { it.hasValue() }
                check(values.size <= 1) { "More than one element has value" }
            }
            XtextExpr_And(gram.subs.mapToImmList { convertExpr(it, if (it.hasValue()) attr else null) })
        }
        is GramExpr_Or -> XtextExpr_Or(gram.subs.mapToImmList { convertExpr(it, attr) })
        is GramExpr_Opt -> XtextExpr_Opt(convertExpr(gram.sub, attr))
        is GramExpr_Rep -> {
            val term = convertExpr(gram.term, attr)
            if (gram.sep == null) {
                XtextExpr_Rep(term, gram.zero)
            } else {
                val sep = convertExpr(gram.sep, null)
                val rep = XtextExpr_Rep(XtextExpr_And(immListOf(sep, term)), true)
                val one = XtextExpr_And(immListOf(term, rep))
                if (gram.zero) XtextExpr_Opt(one) else one
            }
        }
        is GramExpr_Map -> throw IllegalStateException("Map not expected here")
    }

    private fun convertToken(name: String, attr: XtextAttr?): XtextExpr {
        if (attr == null) {
            return convertToken0(name)
        }

        if (name !in xTokenNonterms) {
            val ntName = termNameToXtext("tk$name")
            check(ntName !in xNonterms)
            val expr = convertToken0(name)
            val type = createTokenType(name)
            val prod = XtextProd(type, expr)
            val nonterm = XtextNonterm(ntName)
            nonterm.prods = listOf(prod)
            xTokenNonterms[name] = nonterm
            xNonterms[ntName] = nonterm
        }

        val nonterm = xTokenNonterms.getValue(name)
        val expr = XtextExpr_Symbol(nonterm.name)
        return createAttr(expr, attr)
    }

    private fun convertToken0(name: String): XtextExpr {
        val token = literalTokens[name]
        return if (token != null) XtextExpr_Token(token.pattern) else XtextExpr_Symbol(name)
    }

    private fun createTokenType(name: String): String {
        val tail = if (name !in specialTokens) "" else name.lowercase().capitalizeEx()
        val type = nontermNameToXtext("token$tail")
        if (type !in actions) {
            val token = if (name in specialTokens) name else null
            actions[type] = XtextActionEx(XtextAction_Token(token), null)
        }
        return type
    }

    private fun createAttr(expr: XtextExpr, attr: XtextAttr?): XtextExpr {
        return if (attr == null) expr else XtextExpr_Attr(attr.name, attr.many, expr)
    }

    private fun createAction(type: String, action: XtextAction, transform: ((Any) -> Any)?) {
        check(type !in actions) { type }
        actions[type] = XtextActionEx(action, transform)
    }
}

private object GramExprGen {
    private val parsers = TOP_PARSERS
    private val nonterms = parsers.entries.associate { (k, v) -> v to k }

    fun createGramExpr(parser: Any): GramExpr {
        return createGramExpr0(parser)
    }

    private fun createGramExprSub(parser: Any): GramExpr {
        val nt = nonterms[parser]
        if (nt != null) {
            return GramExpr_Nonterm(nt)
        }
        return createGramExpr0(parser)
    }

    private fun createGramExpr0(parser: Any): GramExpr {
        return when (parser) {
            is ParserReference<*> -> createGramExprSub(parser.parser)
            is RellToken -> GramExpr_Token(parser.name)
            is SkipParser -> GramExpr_Skip(createGramExprSub(parser.innerParser))
            is AndCombinator<*> -> GramExpr_And(GrammarUtils.andParsers(parser).mapToImmList { createGramExprSub(it) })
            is OrCombinator<*> -> GramExpr_Or(GrammarUtils.orParsers(parser).mapToImmList { createGramExprSub(it) })
            is OptionalCombinator<*> -> GramExpr_Opt(createGramExprSub(parser.parser))
            is SeparatedCombinator<*, *> -> {
                val term = createGramExprSub(parser.termParser)
                val sep = createGramExprSub(parser.separatorParser)
                GramExpr_Rep(term, sep, parser.acceptZero)
            }
            is RepeatCombinator<*> -> {
                check(parser.atLeast >= 0)
                checkEquals(parser.atMost, -1)
                GramExpr_Rep(createGramExprSub(parser.parser), null, parser.atLeast == 0)
            }
            is MapCombinator<*, *> -> {
                if (parser.innerParser is SeparatedCombinator<*, *>) {
                    createGramExprSub(parser.innerParser)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    GramExpr_Map(createGramExprSub(parser.innerParser), parser.transform as (Any) -> Any)
                }
            }
            else -> throw IllegalStateException(parser::class.java.simpleName)
        }
    }
}

private fun nontermNameToXtext(name: String): String {
    return "X_" + name.capitalizeEx()
}

private fun termNameToXtext(name: String): String {
    return "X_$name"
}

private class XtextNonterm(val name: String) {
    var prods by LateInit<List<XtextProd>>()

    fun generate(): String {
        val ps = prods.joinToString("\n   | ") { it.generate() }
        return "\n$name\n   : $ps\n   ;"
    }
}

private class XtextProd(private val type: String?, private val expr: XtextExpr) {
    fun generate(): String {
        val s = expr.generate()
        return if (type != null) "{$type} $s" else s
    }
}

private sealed class XtextExpr {
    abstract fun generate(): String
}

private class XtextExpr_Symbol(private val name: String): XtextExpr() {
    override fun generate() = name
}

private class XtextExpr_Token(private val text: String): XtextExpr() {
    override fun generate() = "'$text'"
}

private class XtextExpr_Or(private val subs: ImmList<XtextExpr>): XtextExpr() {
    override fun generate() = "(" + subs.joinToString(" | ") { it.generate() } + ")"
}

private class XtextExpr_And(private val subs: ImmList<XtextExpr>): XtextExpr() {
    override fun generate() = subs.joinToString(" ") { it.generate() }
}

private class XtextExpr_Rep(private val sub: XtextExpr, private val zero: Boolean): XtextExpr() {
    override fun generate() = "(" + sub.generate() + ")" + (if (zero) "*" else "+")
}

private class XtextExpr_Opt(private val sub: XtextExpr): XtextExpr() {
    override fun generate() = "(" + sub.generate() + ")?"
}

private class XtextExpr_Attr(private val attr: String, private val many: Boolean, private val sub: XtextExpr): XtextExpr() {
    override fun generate(): String {
        val op = if (many) "+=" else "="
        return "$attr$op" + sub.generate()
    }
}

private sealed class GramExpr {
    abstract fun hasValue(): Boolean
    abstract fun many(): Boolean
}

private class GramExpr_Token(val name: String): GramExpr() {
    override fun hasValue() = true
    override fun many() = false
}

private class GramExpr_Nonterm(val name: String): GramExpr() {
    override fun hasValue() = true
    override fun many() = false
}

private class GramExpr_Skip(val sub: GramExpr): GramExpr() {
    override fun hasValue() = false
    override fun many() = sub.many()
}

private class GramExpr_Map(val sub: GramExpr, val transform: (Any) -> Any): GramExpr() {
    override fun hasValue() = true
    override fun many() = false
}

private class GramExpr_And(val subs: ImmList<GramExpr>): GramExpr() {
    override fun hasValue() = subs.any { it.hasValue() }
    override fun many() = subs.any { it.many() }
}

private class GramExpr_Or(val subs: ImmList<GramExpr>): GramExpr() {
    override fun hasValue() = subs.any { it.hasValue() }
    override fun many() = subs.any { it.many() }
}

private class GramExpr_Opt(val sub: GramExpr): GramExpr() {
    override fun hasValue() = sub.hasValue()
    override fun many() = sub.many()
}

private class GramExpr_Rep(val term: GramExpr, val sep: GramExpr?, val zero: Boolean): GramExpr() {
    override fun hasValue() = term.hasValue()
    override fun many() = true
}
