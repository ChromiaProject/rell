/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_Name
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.function.Supplier

typealias Getter<T> = () -> T

class Nullable<T> private constructor(val value: T?) {
    override fun equals(other: Any?) = other === this || (other is Nullable<*> && value == other.value)
    override fun hashCode() = if (value == null) 0 else value.hashCode()
    override fun toString(): String = java.lang.String.valueOf(value)

    companion object {
        private val NULL: Nullable<Any> = Nullable(null)

        @Suppress("UNCHECKED_CAST")
        fun <T> of(value: T? = null): Nullable<T> {
            return if (value != null) Nullable(value) else (NULL as Nullable<T>)
        }
    }
}

fun <T> Nullable<T>?.orElse(other: T?): T? = if (this != null) this.value else other

data class One<T>(val value: T)

/** **Project extension** - functionality provided by outer (dependent) projects, e.g. rell-postchain project may
 * provide an extension needed by a component of the rell-base project. This common parent class for all kinds of
 * [ProjExt]s is for the documentation purpose only. */
abstract class ProjExt

class MutableTypedKeyMap {
    private val map = mutableMapOf<TypedKey<Any>, Any>()

    fun <V> put(key: TypedKey<V>, value: V) {
        key as TypedKey<Any>
        check(key !in map)
        map[key] = value as Any
    }

    fun immutableCopy(): TypedKeyMap {
        return TypedKeyMap(map.toMap())
    }
}

class TypedKeyMap(private val map: Map<TypedKey<Any>, Any> = mapOf()) {
    fun <V> get(key: TypedKey<V>): V {
        key as TypedKey<Any>
        val value = map.getValue(key)
        return value as V
    }
}

class TypedKey<V>

fun ByteArray.toBytes(): Bytes = Bytes.of(this)

fun String.hexStringToBytes(): Bytes = this.hexStringToByteArray().toBytes()

fun String.formatSafe(vararg args: Any?): String {
    return try {
        format(Locale.US, *args)
    } catch (e: IllegalFormatException) {
        this
    }
}

fun String.capitalizeEx(): String {
    val c = first()
    return if (!c.isLowerCase()) this else replaceFirstChar { it.titlecase(Locale.US) }
}

class Bytes {
    private val bytes: ByteArray

    private constructor(bytes: ByteArray) {
        this.bytes = bytes
    }

    fun size() = bytes.size

    fun toByteArray() = bytes.clone()
    fun toHex() = bytes.toHex()

    override fun equals(other: Any?) = other === this || (other is Bytes && bytes.contentEquals(other.bytes))
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = bytes.toHex()

    companion object {
        fun of(bytes: ByteArray) = Bytes(bytes.clone())
    }
}

abstract class FixLenBytes(bytes: ByteArray) {
    private val bytes: ByteArray = let {
        val size = size()
        check(bytes.size == size) { "Wrong size: ${bytes.size} instead of $size" }
        bytes.clone()
    }

    abstract fun size(): Int

    fun toByteArray() = bytes.clone()
    fun toHex() = bytes.toHex()
    fun toGtv(): Gtv = GtvFactory.gtv(bytes.clone())

    override fun equals(other: Any?) = other === this
            || (other is FixLenBytes && javaClass == other.javaClass && bytes.contentEquals(other.bytes))
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = bytes.toHex()
}

class Bytes32(bytes: ByteArray): FixLenBytes(bytes) {
    override fun size() = 32

    companion object {
        fun parse(s: String): Bytes32 {
            val bytes = s.hexStringToByteArray()
            return Bytes32(bytes)
        }
    }
}

class Bytes33(bytes: ByteArray): FixLenBytes(bytes) {
    override fun size() = 33

    companion object {
        fun parse(s: String): Bytes33 {
            val bytes = s.hexStringToByteArray()
            return Bytes33(bytes)
        }
    }
}

class BytesKeyPair(val priv: Bytes32, val pub: Bytes33) {
    constructor(priv: ByteArray, pub: ByteArray): this(Bytes32(priv), Bytes33(pub))
}

class LateInit<T: Any>(fallback: T? = null) {
    val getter = LateGetter(this)
    val setter = LateSetter(this)

    private var value: T? = null
    private var fallback: T? = fallback

    fun isSet(): Boolean = value != null

    fun get(): T {
        var res = value
        if (res == null) {
            res = fallback
            checkNotNull(res) { "value not initialized" }
            value = res
            fallback = null
        }
        return res
    }

    fun set(v: T) {
        check(value == null) { "value already initialized with: <$value>" }
        value = v
        fallback = null
    }
}

class LateGetter<T: Any>(private val init: LateInit<T>): Supplier<T> {
    override fun get(): T = init.get()

    companion object {
        fun <T: Any> of(value: T): LateGetter<T> {
            val init = LateInit<T>()
            init.set(value)
            return init.getter
        }
    }
}

class LateSetter<T: Any>(private val init: LateInit<T>) {
    fun set(value: T) = init.set(value)
}

sealed class LazyString {
    abstract val value: String
    final override fun toString() = value

    companion object {
        fun of(value: String): LazyString = ValueLazyString(value)
        fun of(fn: () -> String): LazyString = FnLazyString(fn)
    }
}

private class ValueLazyString(override val value: String): LazyString()

private class FnLazyString(private val fn: () -> String): LazyString() {
    override val value by lazy {
        val res = fn()
        res
    }
}

class LazyPosString(val pos: S_Pos, val lazyStr: LazyString) {
    val str: String get() = lazyStr.value

    override fun toString() = lazyStr.toString()

    companion object {
        fun of(pos: S_Pos, value: String) = LazyPosString(pos, LazyString.of(value))
        fun of(pos: S_Pos, fn: () -> String) = LazyPosString(pos, LazyString.of(fn))
        fun of(cName: C_Name) = of(cName.pos, cName.str)
    }
}

class ThreadLocalContext<T>(private val defaultValue: T? = null) {
    private val local = ThreadLocal.withInitial<T> { defaultValue }

    fun <R> set(value: T, code: () -> R): R {
        val old = local.get()
        local.set(value)
        try {
            val res = code()
            return res
        } finally {
            local.set(old)
        }
    }

    fun get(): T {
        val res = local.get()
        check(res != null)
        return res
    }

    fun getOpt(): T? = local.get()
}

class VersionNumber(items: List<Int>): Comparable<VersionNumber> {
    val items = items.toImmList()

    init {
        for (v in this.items) require(v >= 0) { "wrong version: ${this.items}" }
    }

    fun str(): String = items.joinToString(".")

    override fun compareTo(other: VersionNumber) = CommonUtils.compareLists(items, other.items)
    override fun equals(other: Any?) = other === this || (other is VersionNumber && items == other.items)
    override fun hashCode() = items.hashCode()
    override fun toString() = str()

    companion object {
        fun of(s: String): VersionNumber {
            require(s.matches(Regex("(0|[1-9][0-9]*)([.](0|[1-9][0-9]*))*"))) { "Invalid version format: '$s'" }
            val parts = StringUtils.splitPreserveAllTokens(s, ".")
            val items = parts.map { it.toInt() }
            return VersionNumber(items)
        }
    }
}

class MsgString(s: String) {
    val normal = s.toLowerCase()
    val upper = s.toUpperCase()
    val capital = s.capitalize()

    override fun equals(other: Any?) = other is MsgString && normal == other.normal
    override fun hashCode() = normal.hashCode()
    override fun toString() = normal
}

fun <T> checkEquals(actual: T, expected: T) {
    check(expected == actual) { "expected <$expected> actual <$actual>" }
}

fun <T> checkEquals(actual: T, expected: T, lazyMsg: () -> Any) {
    check(expected == actual) { "expected <$expected> actual <$actual>: ${lazyMsg()}" }
}
