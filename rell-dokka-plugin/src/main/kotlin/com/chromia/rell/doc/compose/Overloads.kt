/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.compose

import com.chromia.rell.doc.model.Doc_Def
import com.chromia.rell.doc.model.Doc_Function

/**
 * Collapse functions that share `(qname, kind)` into a single primary `Doc_Function` carrying
 * the extras in [Doc_Function.overloads]. Build order is preserved: the first occurrence stays
 * as the primary; later occurrences move into `overloads`, in their original order.
 *
 * Non-function defs pass through unchanged. Used by both [SystemBuild] (stdlib has many
 * overloaded signatures — `text.sub`, `byte_array.repeat`, …) and [SourceBuild] (user code
 * can't normally produce same-name functions in one scope, but the same merge is harmless and
 * keeps the two builders symmetric).
 */
internal fun groupFunctionOverloads(defs: List<Doc_Def>): List<Doc_Def> {
    if (defs.size < 2) return defs
    val out = ArrayList<Doc_Def>(defs.size)
    // qname+kind → index in `out`. Only Doc_Functions populate this; non-function defs go
    // straight through without ever joining a group.
    val seen = HashMap<Pair<String, com.chromia.rell.doc.model.Doc_FunctionKind>, Int>()
    for (def in defs) {
        if (def !is Doc_Function) {
            out.add(def)
            continue
        }
        val key = def.qname to def.kind
        val existingIdx = seen[key]
        if (existingIdx == null) {
            seen[key] = out.size
            out.add(def)
        } else {
            val primary = out[existingIdx] as Doc_Function
            out[existingIdx] = primary.copy(overloads = primary.overloads + def)
        }
    }
    return out
}
