/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.PostchainGtvUtils
import java.io.File

fun interface ReplInputChannelFactory {
    fun createInputChannel(historyFile: File?): ReplInputChannel
}

interface ReplInputChannel {
    fun readLine(prompt: String): String?
}

fun interface ReplOutputChannelFactory {
    fun createOutputChannel(): ReplOutputChannel
}

interface ReplOutputChannel {
    fun printInfo(msg: String)
    fun printCompilerError(code: String, msg: String)
    fun printCompilerMessage(message: C_Message)
    fun printRuntimeError(e: Rt_Exception)
    fun printPlatformRuntimeError(e: Throwable)
    fun setValueFormat(format: ReplValueFormat)
    fun printValue(value: Rt_Value)
    fun printControl(code: String, msg: String)
}

enum class ReplValueFormat {
    DEFAULT,
    ONE_ITEM_PER_LINE,
    GTV_STRING,
    GTV_JSON,
    GTV_XML
}

object ReplValueFormatter {
    fun format(v: Rt_Value, format: ReplValueFormat): String? {
        val res = when (format) {
            ReplValueFormat.DEFAULT -> formatDefault(v)
            ReplValueFormat.ONE_ITEM_PER_LINE -> formatOneItemPerLine(v)
            ReplValueFormat.GTV_STRING -> formatGtvString(v)
            ReplValueFormat.GTV_JSON -> formatGtvJson(v)
            ReplValueFormat.GTV_XML -> formatGtvXml(v)
        }
        return res
    }

    private fun formatDefault(v: Rt_Value): String? {
        return if (v == Rt_UnitValue) null else v.str()
    }

    private fun formatStrict(v: Rt_Value): String {
        return v.strCode()
    }

    private fun formatOneItemPerLine(v: Rt_Value): String? {
        if (v == Rt_UnitValue) return null
        val type = v.type()
        return when (type) {
            is R_CollectionType -> collectionToLines(v.asCollection()) { it.str() }
            is R_MapType -> collectionToLines(v.asMap().entries) { "${it.key.str()}=${it.value.str()}" }
            else -> v.str()
        }
    }

    private fun <T> collectionToLines(c: Collection<T>, stringifier: (T) -> String): String? {
        return if (c.isEmpty()) null else c.joinToString("\n") { stringifier(it) }
    }

    private fun formatGtvString(v: Rt_Value): String? {
        return formatGtv(v) {
            it.toString()
        }
    }

    private fun formatGtvJson(v: Rt_Value): String? {
        return formatGtv(v) {
            PostchainGtvUtils.gtvToJsonPretty(it)
        }
    }

    private fun formatGtvXml(v: Rt_Value): String? {
        return formatGtv(v) {
            val xml = PostchainGtvUtils.gtvToXml(it)
            val res = xml.removePrefix("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            res
        }
    }

    private fun formatGtv(v: Rt_Value, gtvToString: (Gtv) -> String): String? {
        if (v == Rt_UnitValue) return null

        val type = v.type()
        val flags = type.completeFlags()
        if (!flags.gtv.toGtv) {
            return "Type ${type.str()} cannot be converted to Gtv. Switch to a different output format."
        }

        val gtv = type.rtToGtv(v, true)
        val res = gtvToString(gtv).trim()
        return res
    }
}
