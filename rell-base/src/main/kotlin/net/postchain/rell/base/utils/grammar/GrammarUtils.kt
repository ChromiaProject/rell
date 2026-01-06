/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.grammar

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.ParserReference
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.parser.Parser
import net.postchain.rell.base.compiler.parser.LegacyCombinator
import net.postchain.rell.base.compiler.parser.RellToken
import net.postchain.rell.base.compiler.parser.S_Grammar
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.mapValuesToImmMap
import net.postchain.rell.base.utils.toImmMap
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.time.FastDateFormat
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object GrammarUtils {
    private val PARSERS: ImmMap<String, Parser<*>> = makeParsers()

    fun getParsers(): ImmMap<String, Parser<*>> = PARSERS

    private fun makeParsers(): ImmMap<String, Parser<*>> {
        val parsers = mutableMapOf<String, Parser<*>>()

        for (p in S_Grammar::class.memberProperties) {
            p.isAccessible = true

            val v = try {
                p.getter.call(S_Grammar)
            } catch (e: IllegalArgumentException) {
                p.getter.call()
            }

            if (v is RellToken) {
                // ignore
            } else if (v is Parser<*>) {
                parsers[p.name] = v
            }
        }

        for ((i, p) in S_Grammar.commaSeparatedParsers.withIndex()) {
            val name = "commaSeparated_$i"
            parsers[name] = p
        }

        return reduceParsers(parsers.toImmMap())
    }

    fun andParsers(p: Any): List<Any> {
        return if (p is AndCombinator<*>) {
            @Suppress("DEPRECATION") // more convenient than use type-safe lists
            p.consumers.flatMap { andParsers(it) }
        } else {
            listOf(p)
        }
    }

    fun orParsers(p: Any): List<Any> {
        return if (p is OrCombinator<*>) {
            p.parsers.flatMap { orParsers(it) }
        } else {
            listOf(p)
        }
    }

    private val formatter: FastDateFormat = FastDateFormat.getInstance(
        "yyyy-MM-dd HH:mm:ssZ",
        TimeZone.getTimeZone("UTC"),
        Locale.US,
    )

    fun timestampToString(timestamp: Long): String = formatter.format(timestamp)

    private fun reduceParsers(parsers: Map<String, Parser<*>>): ImmMap<String, Parser<*>> {
        val replacements = mutableMapOf<Parser<*>, Parser<*>>()
        val newParsers = mutableMapOf<String, Parser<*>>()

        for ((name, parser) in parsers) {
            if (parser is LegacyCombinator<*>) {
                if (parser.reducedParser != null) {
                    newParsers[name] = parser
                    replacements[parser] = parser.reducedParser
                }
            } else {
                newParsers[name] = parser
            }
        }

        val resParsers = replaceParsers(newParsers.toImmMap(), replacements.toImmMap())
        verifyReducedParsers(resParsers)

        return resParsers
    }

    // Make sure no legacy combinators left (to be on the safe side).
    private fun verifyReducedParsers(parsers: Map<String, Parser<*>>) {
        val topMap = MapUtils.invertMap(parsers).toImmMap()
        val visited = mutableSetOf<Parser<*>>()

        fun verifyParser(parser: Parser<*>) {
            if (!visited.add(parser)) {
                return
            }

            check(parser !is LegacyCombinator<*>)

            when (parser) {
                is RellToken -> check(parser.isEnabled(RellVersions.VERSION)) { parser.name }
                is ParserReference<*> -> {
                    check(parser.parser in topMap)
                }
                else -> transformInnerParsers(parser) {
                    verifyParser(it)
                    it
                }
            }
        }

        for (parser in parsers.values) {
            verifyParser(parser)
        }
    }

    private fun replaceParsers(
        parsers: ImmMap<String, Parser<*>>,
        replacements: ImmMap<Parser<*>, Parser<*>>,
    ): ImmMap<String, Parser<*>> {
        if (replacements.isEmpty()) {
            return parsers
        }

        val replacer = Replacer(replacements)

        return parsers.mapValuesToImmMap { replacer.replace(it.value) }
    }

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION") // consumers preserve order
    private fun transformInnerParsers(parser: Parser<*>, fn: (Parser<*>) -> Parser<*>): Parser<*> {
        return when (parser) {
            is AndCombinator<*> -> {
                val consumers = parser.consumers.map { sub ->
                    when (sub) {
                        is Parser<*> -> fn(sub)
                        is SkipParser -> {
                            val skipSub = fn(sub.innerParser)
                            if (skipSub == sub.innerParser) sub else SkipParser(skipSub)
                        }
                        else -> throw UnsupportedOperationException(sub.javaClass.simpleName)
                    }
                }

                // Usage of the internal constructors can be imitated but is too verbose
                if (consumers == parser.consumers) parser else AndCombinator(consumers, parser.transform)
            }
            is OrCombinator<*> -> {
                val innerParsers = parser.parsers.map { fn(it) }
                if (innerParsers == parser.parsers) parser else OrCombinator(innerParsers)
            }
            is OptionalCombinator<*> -> {
                val innerParser = fn(parser.parser)
                if (innerParser == parser.parser) parser else OptionalCombinator(innerParser)
            }
            is RepeatCombinator<*> -> {
                val innerParser = fn(parser.parser)
                if (innerParser == parser.parser) parser else RepeatCombinator(innerParser, parser.atLeast, parser.atMost)
            }
            is SeparatedCombinator<*, *> -> {
                val termParser = fn(parser.termParser)
                val separatorParser = fn(parser.separatorParser)
                when {
                    termParser == parser.termParser && separatorParser == parser.separatorParser -> parser
                    else -> SeparatedCombinator(termParser, separatorParser, parser.acceptZero)
                }
            }
            is MapCombinator<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                parser as MapCombinator<Any?, Any?>
                val innerParser = fn(parser.innerParser)
                if (innerParser == parser.innerParser) parser else MapCombinator(innerParser, parser.transform)
            }

            // Tokens and references are not supported - to be handled by the caller.
            else -> throw java.lang.IllegalArgumentException(parser.javaClass.simpleName)
        }
    }

    private class Replacer(private val replacements: ImmMap<Parser<*>, Parser<*>>) {
        private val started = mutableSetOf<Parser<*>>()
        private val finished = mutableMapOf<Parser<*>, Parser<*>>()
        private val refs = mutableMapOf<Parser<*>, ParserRef>()
        private val newRefs = ArrayDeque<Parser<*>>()

        fun replace(parser: Parser<*>): Parser<*> {
            val res = replacePrivate(parser)
            processRefs()
            return res
        }

        private fun processRefs() {
            while (newRefs.isNotEmpty()) {
                val parser = newRefs.removeFirst()
                val ref = refs.getValue(parser)
                val target = replacePrivate(parser)
                ref.set(target)
            }
        }

        private fun replacePrivate(parser: Parser<*>): Parser<*> {
            val replacement = replacements[parser]
            if (replacement != null) {
                return replacePrivate(replacement)
            }

            var res = finished[parser]

            if (res == null) {
                check(started.add(parser))

                res = when (parser) {
                    is RellToken -> parser
                    is ParserReference<*> -> processReference(parser)
                    else -> transformInnerParsers(parser, ::replacePrivate)
                }

                finished[parser] = res
            }

            return res
        }

        private fun processReference(parser: ParserReference<*>): Parser<*> {
            val target = parser.parser
            val ref = refs.getOrPut(target) {
                newRefs.add(target)
                ParserRef()
            }
            return parser { ref.get() }
        }

        private class ParserRef {
            private var target: Parser<*>? = null

            fun get(): Parser<*> = target!!

            fun set(target: Parser<*>) {
                check(this.target == null)
                this.target = target
            }
        }
    }
}
