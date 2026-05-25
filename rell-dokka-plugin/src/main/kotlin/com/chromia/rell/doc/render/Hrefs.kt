/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

/**
 * Relative-href helper. Every page's URL is its on-disk path relative to the site root; given
 * the current page's path and a target page's path, we compute the `../`-prefixed path that lets
 * a `<a href="…">` work without depending on an absolute base URL.
 *
 * Both inputs are already in forward-slash form (we never write Windows paths into HTML).
 */
internal object Hrefs {
    fun relativeFrom(from: String, to: String): String {
        val fromParts = from.split('/').dropLast(1)
        val toParts = to.split('/')
        var common = 0
        while (common < fromParts.size && common < toParts.size - 1 && fromParts[common] == toParts[common]) {
            common++
        }
        val up = fromParts.size - common
        val result = buildString {
            repeat(up) { append("../") }
            for (i in common until toParts.size) {
                if (i > common) append('/')
                append(toParts[i])
            }
        }
        return result.ifEmpty { "./" }
    }
}
