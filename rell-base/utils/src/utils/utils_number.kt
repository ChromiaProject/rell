/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

fun Long.toIntExact(): Int = Math.toIntExact(this)

fun Long.toIntExactOrNull(): Int? = if (toInt().toLong() == this) {
    toInt()
} else {
    null
}
