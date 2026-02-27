/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.grammar

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.ParserReference
import com.github.h0tk3y.betterParse.parser.Parser
import net.postchain.rell.base.compiler.parser.RellToken
import net.postchain.rell.base.compiler.parser.RellTokens
import net.postchain.rell.base.utils.RellVersions

fun main() {
    val timestamp = System.currentTimeMillis()
    val tokens = RellTokens.DEFAULT

    println("var RELL_GRAMMAR = {")

    println("    \"version\": \"${RellVersions.VERSION_STR}\",")
    println("    \"timestamp\": \"$timestamp\",")
    println("    \"timestampStr\": \"${GrammarUtils.timestampToString(timestamp)}\",")
    println("    \"lex\": {")
    println("        \"generals\": {")
    println("            \"IDENTIFIER\": \"${tokens.identifier.name}\",")
    println("            \"INTEGER\": \"${tokens.integer.name}\",")
    println("            \"BIG_INTEGER\": \"${tokens.bigInteger.name}\",")
    println("            \"DECIMAL\": \"${tokens.decimal.name}\",")
    println("            \"STRING\": \"${tokens.string.name}\",")
    println("            \"BYTE_ARRAY\": \"${tokens.byteArray.name}\",")
    println("        },")
    println("        \"keywords\": {")
    for (t in tokens.keywords.sortedBy { it.pattern }) {
        println("            \"${t.pattern}\": \"${t.name}\",")
    }
    println("        },")
    println("        \"delims\": {")
    for (t in tokens.delims.sortedBy { it.pattern }) {
        println("            \"${t.pattern}\": \"${t.name}\",")
    }
    println("        },")
    println("    },")

    val nameToParser = GrammarUtils.getParsers()
    val parserToName = nameToParser.entries.associate { (k, v) -> v to k }

    println("    \"syntax\": {")

    val refs = mutableSetOf("rootParser")
    val nameToJs = nameToParser.mapValues {
        parserToJavascript(parserToName, it.value, refs, true)
    }

    for (name in nameToJs.keys.sorted()) {
        if (name in refs) {
            val js = nameToJs.getValue(name)
            println("        \"$name\": $js,")
        }
    }

    println("    },")
    println("};");
}

private fun parserToJavascript(
    nameMap: Map<Parser<*>, String>,
    parser: Any,
    refs: MutableSet<String>,
    top: Boolean,
): String {
    if (!top && parser in nameMap) {
        val name = nameMap.getValue(parser as Parser<*>)
        refs.add(name)
        return """"$name""""
    }

    return when (parser) {
        is RellToken -> "\"${parser.name}\""
        is MapCombinator<*, *> -> parserToJavascript(nameMap, parser.innerParser, refs, false)
        is SkipParser -> parserToJavascript(nameMap, parser.innerParser, refs, false)
        is AndCombinator<*> -> {
            val subs = GrammarUtils.andParsers(parser)
            val ps = subs.joinToString(",") { parserToJavascript(nameMap, it, refs, false) }
            """{"type":"and","parsers":[$ps]}"""
        }
        is OrCombinator<*> -> {
            val subs = GrammarUtils.orParsers(parser)
            val ps = subs.joinToString(",") { parserToJavascript(nameMap, it, refs, false) }
            """{"type":"or","parsers":[$ps]}"""
        }
        is RepeatCombinator<*> -> {
            check(parser.atLeast >= 0)
            check(parser.atMost == -1)
            val zero = parser.atLeast == 0
            val sub = parserToJavascript(nameMap, parser.parser, refs, false)
            """{"type":"rep","zero":$zero,"parser":$sub}"""
        }
        is SeparatedCombinator<*, *> -> {
            val term = parserToJavascript(nameMap, parser.termParser, refs, false)
            val sep = parserToJavascript(nameMap, parser.separatorParser, refs, false)
            """{"type":"sep","zero":${parser.acceptZero},"term":$term,"sep":$sep}"""
        }
        is OptionalCombinator<*> -> {
            val sub = parserToJavascript(nameMap, parser.parser, refs, false)
            """{"type":"opt","parser":$sub}"""
        }
        is ParserReference<*> -> parserToJavascript(nameMap, parser.parser, refs, false)
        else -> throw IllegalStateException(parser::class.java.simpleName)
    }
}
