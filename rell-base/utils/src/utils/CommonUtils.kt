/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.min

object CommonUtils {
    val IS_UNIT_TEST: Boolean = Thread.currentThread().stackTrace.any { it.className.startsWith("org.junit.") }

    // java.util.HexFormat (JDK 17+) is not emulated by TeaVM's classlib — touching it here
    // (via the static initialiser) would break reachability for the whole utils package on
    // the browser build. Inlined hex codecs do the same job and are dependency-free.
    private const val HEX_DIGITS = "0123456789abcdef"

    fun bytesToHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX_DIGITS[b ushr 4]
            out[i * 2 + 1] = HEX_DIGITS[b and 0xF]
        }
        return String(out)
    }

    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex length: ${hex.length}" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character at index ${i * 2}" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun <T> split(lst: MutableList<T>, partSize: Int): List<MutableList<T>> {
        val s = lst.size
        if (s <= partSize) {
            return listOf(lst)
        }

        val parts = (s + partSize - 1) / partSize
        val res = (0 until parts).map { lst.subList(it * partSize, min((it + 1) * partSize, s)) }
        return res
    }

    fun <T: Comparable<T>> compareLists(l1: List<T>, l2: List<T>): Int {
        val n1 = l1.size
        val n2 = l2.size
        for (i in 0 until min(n1, n2)) {
            val c = l1[i].compareTo(l2[i])
            if (c != 0) {
                return c
            }
        }
        return n1.compareTo(n2)
    }

    /** Invokes the key getter strictly one time for each item (builds list of pairs (item, key), then sorts). */
    inline fun <T, K: Comparable<K>> sortedByCopy(data: Collection<T>, keyGetter: (T) -> K): ImmList<T> {
        return data
            .map { it to keyGetter(it) }
            .sortedBy { it.second }
            .mapToImmList { it.first }
    }

    fun readFileText(path: String): String {
        /*
        * FYI: We use Spring convention here when files under resources are labeled with prefix 'classpath:'.
        * */
        val resourcePrefix = "classpath:"
        return if (path.startsWith(resourcePrefix)) {
            val resourcePath = path.substringAfter(resourcePrefix)
            val resource = javaClass.getResource(resourcePath)
            checkNotNull(resource) { "File not found: $resourcePath" }
            resource.readText()
        } else {
            File(path).readText()
        }
    }

    fun <T> calcOpt(f: () -> T): T? = try {
        f()
    } catch (e: Throwable) {
        null
    }

    fun getHomeDir(): File? {
        val homePath = System.getProperty("user.home") ?: return null
        val homeDir = File(homePath)
        return if (homeDir.isDirectory) homeDir else null
    }

    fun <T> chainToList(first: T?, nextGetter: (T) -> T?): ImmList<T> {
        if (first == null) return immListOf()

        val res = mutableListOf<T>()
        var cur = first
        while (cur != null) {
            res.add(cur)
            cur = nextGetter(cur)
        }

        return res.toImmList()
    }

    fun tableToStrings(table: List<List<String>>): List<String> {
        val widths = mutableListOf<Int>()

        for (row in table) {
            for ((i, cell) in row.withIndex()) {
                if (widths.size <= i) widths.add(0)
                widths[i] = max(widths[i], cell.length)
            }
        }

        return table.map { row ->
            row.mapIndexed { i, cell -> if (i == row.size - 1) cell else cell.padEnd(widths[i]) }
                .joinToString("   ")
        }
    }

    fun failIfUnitTest() {
        // Don't fail if called for a debug evaluation - IntelliJ calls toString() to show variable values.
        check(!IS_UNIT_TEST || isIntelliJDebugEvaluation())
    }

    private fun isIntelliJDebugEvaluation(): Boolean {
        val stack = Thread.currentThread().stackTrace
        val ij = stack.withIndex().find { it.value.className.startsWith("com.intellij.rt.debugger.") }
        ij ?: return false
        return stack.withIndex().any {
            it.index > ij.index && it.value.className.startsWith("net.postchain.rell.")
        }
    }
}
