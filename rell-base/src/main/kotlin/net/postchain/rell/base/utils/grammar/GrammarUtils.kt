/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.grammar

import com.github.h0tk3y.betterParse.combinators.AndCombinator
import com.github.h0tk3y.betterParse.combinators.OrCombinator
import com.github.h0tk3y.betterParse.parser.Parser
import net.postchain.rell.base.compiler.parser.LegacyCombinator
import net.postchain.rell.base.compiler.parser.S_Grammar
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.toImmMap
import org.apache.commons.lang3.time.FastDateFormat
import java.util.*

object GrammarUtils {
    private val PARSERS: ImmMap<String, Parser<*>> by lazy {
        val reducedParsers = S_Grammar.parsersByName.mapNotNull { (k, parser) ->
            when (parser) {
                // Replace LegacyCombinator with its reducedParser if it is present
                is LegacyCombinator<*> -> parser.reducedParser
                else -> parser
            }?.let { k to it }
        }

        val commaSeparatedParsers = S_Grammar.commaSeparatedParsers.mapIndexed { i, p -> "commaSeparated_$i" to p }
        (reducedParsers + commaSeparatedParsers).toImmMap()
    }

    fun getParsers() = PARSERS

    fun andParsers(p: Any): List<Any> {
        val reduced = reduceLegacy(p)
        return if (reduced is AndCombinator<*>) {
            reduced.parsers
                .zip(reduced.skipParsers)
                .map { (p, s) -> (p ?: s)!! }
                .flatMap { andParsers(it) }
        } else {
            listOf(reduced)
        }
    }

    fun orParsers(p: Any): List<Any> {
        val reduced = reduceLegacy(p)
        return if (reduced is OrCombinator<*>) {
            reduced.parsers.flatMap { orParsers(it) }
        } else {
            listOf(reduced)
        }
    }

    private fun reduceLegacy(p: Any): Any = if (p is LegacyCombinator<*>) {
        reduceLegacy(p.reducedParser ?: p.innerParser)
    } else {
        p
    }

    private val formatter: FastDateFormat = FastDateFormat.getInstance(
        "yyyy-MM-dd HH:mm:ssZ",
        TimeZone.getTimeZone("UTC"),
        Locale.US,
    )

    fun timestampToString(timestamp: Long): String = formatter.format(timestamp)
}
