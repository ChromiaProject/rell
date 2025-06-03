/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.runcfg

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.gtv.builder.GtvBuilder

object RunConfigGtvParser {
    fun parseGtv(elem: RellXmlElement): GtvBuilder.GtvNode {
        return parseTopGtv(elem, true)
    }

    fun parseGtvRaw(elem: RellXmlElement): Gtv {
        val gtvNode = parseTopGtv(elem, false)
        return gtvNode.toGtv()
    }

    private fun parseTopGtv(elem: RellXmlElement, mergeAllowed: Boolean): GtvBuilder.GtvNode {
        elem.checkNoText()
        elem.check(elem.elems.size == 1) { "expected exactly one nested element, but found ${elem.elems.size}" }
        return parseGtvNode(elem.elems[0], mergeAllowed)
    }

    fun parseGtvNode(elem: RellXmlElement, mergeAllowed: Boolean): GtvBuilder.GtvNode {
        return when (elem.tag) {
            "null" -> {
                elem.attrs().checkNoMore()
                elem.checkNoText()
                elem.checkNoElems()
                GtvBuilder.GtvTermNode(GtvNull)
            }
            "int" -> {
                elem.attrs().checkNoMore()
                elem.checkNoElems()
                val value = elem.parseText { it.toLong() }
                GtvBuilder.GtvTermNode(GtvFactory.gtv(value))
            }
            "string" -> {
                elem.attrs().checkNoMore()
                elem.checkNoElems()
                val value = elem.text ?: ""
                GtvBuilder.GtvTermNode(GtvFactory.gtv(value))
            }
            "bytea" -> {
                elem.attrs().checkNoMore()
                elem.checkNoElems()
                val value = elem.parseText { it.hexStringToByteArray() }
                GtvBuilder.GtvTermNode(GtvFactory.gtv(value))
            }
            "array" -> parseGtvArray(elem, mergeAllowed)
            "dict" -> parseGtvDict(elem, mergeAllowed)
            else -> throw elem.errorTag()
        }
    }

    private fun parseGtvArray(elem: RellXmlElement, mergeAllowed: Boolean): GtvBuilder.GtvNode {
        val attrs = elem.attrs()
        val merge = if (!mergeAllowed) null else attrs.getTypeOpt("merge", null, parser = { GtvBuilder.GtvArrayMerge.parse(it) })
        attrs.checkNoMore()

        elem.checkNoText()

        val values = elem.elems.map { parseGtvNode(it, mergeAllowed) }
        return GtvBuilder.GtvArrayNode(values, merge ?: GtvBuilder.GtvArrayMerge.APPEND)
    }

    private fun parseGtvDict(elem: RellXmlElement, mergeAllowed: Boolean): GtvBuilder.GtvNode {
        val attrs = elem.attrs()
        val merge0 = if (!mergeAllowed) null else attrs.getTypeOpt("merge", null, parser = { GtvBuilder.GtvDictMerge.parseDict(it) })
        val merge = merge0 ?: GtvBuilder.GtvDictMerge.KEEP_NEW
        attrs.checkNoMore()
        elem.checkNoText()

        val map = mutableMapOf<String, GtvBuilder.GtvDictEntry>()
        for (sub in elem.elems) {
            val (key, entry) = parseGtvDictEntry(sub, merge, mergeAllowed)
            sub.check(key !in map) { "duplicate entry key: '$key'" }
            map[key] = entry
        }

        return GtvBuilder.GtvDictNode(map, merge)
    }

    private fun parseGtvDictEntry(
            elem: RellXmlElement,
            dictMerge: GtvBuilder.GtvDictMerge,
            mergeAllowed: Boolean
    ): Pair<String, GtvBuilder.GtvDictEntry> {
        elem.checkTag("entry")
        elem.checkNoText()

        val attrs = elem.attrs()
        val key = attrs.get("key")
        val merge = if (!mergeAllowed) null else attrs.getTypeOpt("merge", null, parser = { GtvBuilder.GtvDictMerge.parseEntry(it) })
        attrs.checkNoMore()

        val value = parseGtv(elem)
        return key to GtvBuilder.GtvDictEntry(value, merge ?: dictMerge)
    }
}
