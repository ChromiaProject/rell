/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory

class Bytes private constructor(private val bytes: ByteArray) {
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

fun ByteArray.toBytes(): Bytes = Bytes.of(this)
