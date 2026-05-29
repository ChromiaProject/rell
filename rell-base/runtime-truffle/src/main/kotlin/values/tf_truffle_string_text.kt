/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.values

import com.oracle.truffle.api.strings.TruffleString
import net.postchain.rell.base.runtime.Rt_JavaStringText
import net.postchain.rell.base.runtime.Rt_TextValue

/**
 * Truffle-string-backed text leaf for the Truffle JIT hot path. Holds a [TruffleString], which
 * supports zero-copy slicing, native UTF-8/UTF-16 conversion, and PE-friendly concat — all of
 * which spend less than the Java-`String` equivalents on text-heavy inner loops.
 *
 * The Java-`String` view materialises lazily on first access (e.g. when the value escapes through
 * a sys-fn that hasn't been Truffle-specialised, or hits GTV/SQL serialisation). Cross-leaf
 * equality with [Rt_JavaStringText] funnels through [Rt_TextValue.equals] which compares Strings.
 *
 * Encoding is fixed to UTF-16 to align with Java/Kotlin String semantics; cross-encoding
 * specialisations would belong in dedicated nodes.
 */
data class Tf_TruffleStringText(val ts: TruffleString): Rt_TextValue {
    override fun equals(other: Any?): Boolean = other === this || (other is Rt_TextValue && value == other.value)
    override fun hashCode(): Int = value.hashCode()

    @Volatile
    private var cachedJavaString: String? = null

    override val value: String
        get() {
            val cached = cachedJavaString
            if (cached != null) return cached
            val materialized = ts.toJavaStringUncached()
            cachedJavaString = materialized
            return materialized
        }

    companion object {
        val ENCODING: TruffleString.Encoding = TruffleString.Encoding.UTF_16

        fun fromJavaString(s: String): Tf_TruffleStringText =
            Tf_TruffleStringText(TruffleString.fromJavaStringUncached(s, ENCODING))
    }
}
