/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter
import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta

object Diff {
    fun diffInline(original: String, revised: String): List<AbstractDelta<String>> {
        val patch = DiffUtils.diffInline(original, revised)
        return patch.deltas
    }
}
