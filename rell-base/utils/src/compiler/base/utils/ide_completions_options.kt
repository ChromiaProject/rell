/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.utils.toImmMap

data class C_IdeCompletionsOptions(
    val file: C_SourcePath,
    val pos: Int?,
) {
    init {
        require(pos == null || pos >= 0)
    }

    fun toPojo(): Map<String, Any> {
        val res = mutableMapOf<String, Any>("file" to file.str())
        if (pos != null) res["pos"] = pos
        return res.toImmMap()
    }

    companion object {
        fun fromPojo(map: Map<String, Any>): C_IdeCompletionsOptions {
            val file = C_SourcePath.parse(map.getValue("file") as String)
            val pos = map["pos"]?.let { it as Int }
            return C_IdeCompletionsOptions(file, pos)
        }
    }
}
