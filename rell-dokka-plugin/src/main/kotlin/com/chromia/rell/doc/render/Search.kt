/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.Doc_Class
import com.chromia.rell.doc.model.Doc_Function
import com.chromia.rell.doc.model.Doc_Site

/**
 * Writes `scripts/pages.json` — the search-index payload. Schema matches the previous Dokka
 * output (`name`, `description`, `location`, `searchKeys`) so any consumers can keep using the
 * same shape. Anonymous functions (`function#N`) have their `#` URL-escaped to `%23` since
 * `#` would otherwise be a fragment separator.
 */
internal object Search {

    /**
     * Emit the JSON payload as a plain string — no kotlinx.serialization dep needed. The shape
     * is small and stable: `[{"name":"…","description":"…","location":"…","searchKeys":[…]}, …]`.
     */
    fun render(site: Doc_Site): String {
        val records = mutableListOf<Record>()
        for (module in site.modules) {
            for (pkg in module.packages) {
                if (pkg.qname in site.hiddenPackages) continue
                records += Record(
                    name = pkg.displayName,
                    description = "",
                    location = Paths.urlEncodeName(Paths.packageIndexPath(module, pkg)),
                    searchKeys = listOf(pkg.qname, pkg.displayName),
                )
                for (def in pkg.defs) {
                    val name = renderDefSearchName(def)
                    records += Record(
                        name = name,
                        description = pkg.qname,
                        location = Paths.urlEncodeName(Paths.pageRelativePath(module, pkg, def)),
                        searchKeys = listOf(def.qname, def.name),
                    )
                    if (def is Doc_Class) {
                        for (member in def.members) {
                            records += Record(
                                name = "${def.name}.${member.name}",
                                description = pkg.qname,
                                location = Paths.urlEncodeName(Paths.memberRelativePath(module, pkg, def, member)),
                                searchKeys = listOf(member.qname, member.name),
                            )
                        }
                    }
                }
            }
        }
        return renderJson(records)
    }

    private fun renderDefSearchName(def: com.chromia.rell.doc.model.Doc_Def): String =
        if (def is Doc_Function) "${def.name}()" else def.name

    private data class Record(
        val name: String,
        val description: String,
        val location: String,
        val searchKeys: List<String>,
    )

    private fun renderJson(records: List<Record>): String = buildString {
        append('[')
        records.forEachIndexed { i, r ->
            if (i > 0) append(',')
            append('{')
            appendStringField("name", r.name); append(',')
            appendStringField("description", r.description); append(',')
            appendStringField("location", r.location); append(',')
            append("\"searchKeys\":[")
            r.searchKeys.forEachIndexed { j, key ->
                if (j > 0) append(',')
                appendJsonString(key)
            }
            append(']')
            append('}')
        }
        append(']')
    }

    private fun Appendable.appendStringField(key: String, value: String) {
        append('"').append(key).append("\":")
        appendJsonString(value)
    }

    private fun Appendable.appendJsonString(s: String) {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            in Char(0x00)..Char(0x1F) -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }
}
