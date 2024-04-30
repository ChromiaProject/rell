/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.grammar

import com.github.h0tk3y.betterParse.combinators.AndCombinator
import com.github.h0tk3y.betterParse.combinators.OrCombinator
import com.github.h0tk3y.betterParse.parser.Parser
import net.postchain.rell.base.compiler.parser.RellToken
import net.postchain.rell.base.compiler.parser.S_Grammar
import net.postchain.rell.base.utils.toImmMap
import org.apache.commons.lang3.time.FastDateFormat
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object GrammarUtils {
    fun getParsers(): Map<String, Parser<*>> {
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

        return parsers.toImmMap()
    }

    fun andParsers(p: Any): List<Any> {
        return if (p is AndCombinator<*>) {
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

    fun timestampToString(timestamp: Long): String {
        val tz = TimeZone.getTimeZone("UTC")
        return FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ssZ", tz).format(timestamp)
    }
}
