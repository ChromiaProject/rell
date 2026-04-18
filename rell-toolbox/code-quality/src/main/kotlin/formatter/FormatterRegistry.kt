/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter

import org.antlr.v4.runtime.ParserRuleContext

class FormatterRegistry {

    private val formatters: MutableMap<Class<out ParserRuleContext>, NodeFormatter<out ParserRuleContext>> =
        mutableMapOf()

    fun <T : ParserRuleContext> findFormatter(nodeType: Class<T>): NodeFormatter<T>? {
        @Suppress("UNCHECKED_CAST")
        return formatters[nodeType] as? NodeFormatter<T>
    }

    fun <T : ParserRuleContext> register(nodeType: Class<T>, formatter: NodeFormatter<T>) {
        formatters[nodeType] = formatter
    }

    fun clear() {
        formatters.clear()
    }
}
