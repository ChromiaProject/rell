/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
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
    fun format(v: Rt_Value, format: ReplValueFormat): String? = when (format) {
        ReplValueFormat.DEFAULT -> formatDefault(v)
        ReplValueFormat.ONE_ITEM_PER_LINE -> formatOneItemPerLine(v)
        ReplValueFormat.GTV_STRING -> formatGtvString(v)
        ReplValueFormat.GTV_JSON -> formatGtvJson(v)
        ReplValueFormat.GTV_XML -> formatGtvXml(v)
    }

    private fun formatDefault(v: Rt_Value): String? = if (v === Rt_UnitValue) null else v.str()

    private fun formatOneItemPerLine(v: Rt_Value): String? = when (v.type.rrType) {
        is RR_Type.List, is RR_Type.Set -> collectionToLines(v.asCollection()) { it.str() }
        is RR_Type.Map -> collectionToLines(v.asMap().entries) { "${it.key.str()}=${it.value.str()}" }
        else -> v.str()
    }

    private fun <T> collectionToLines(c: Collection<T>, stringifier: (T) -> String): String? =
        if (c.isEmpty()) null else c.joinToString("\n") { stringifier(it) }

    private fun formatGtvString(v: Rt_Value): String? = formatGtv(v) { it.toString() }

    private fun formatGtvJson(v: Rt_Value): String? = formatGtv(v) {
        PostchainGtvUtils.gtvToJsonPretty(it)
    }

    private fun formatGtvXml(v: Rt_Value): String? = formatGtv(v) {
        val xml = PostchainGtvUtils.gtvToXml(it)
        val res = xml.removePrefix("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        res
    }

    private fun formatGtv(v: Rt_Value, gtvToString: (Gtv) -> String): String? {
        if (v == Rt_UnitValue) return null

        val rtType = v.type
        val gtvConv = rtType.gtvConversion
            ?: return "Type ${rtType.name} cannot be converted to Gtv. Switch to a different output format."

        val gtv = try {
            gtvConv.rtToGtv(v, true)
        } catch (e: Exception) {
            return "Type ${rtType.name} cannot be converted to Gtv: ${e.message}"
        }

        return gtvToString(gtv).trim()
    }
}
