/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

private const val HEX_CHARS = "0123456789ABCDEF"
private val HEX_CHAR_ARRAY = HEX_CHARS.toCharArray()

fun ByteArray.toHex(): String {
    val result = StringBuilder()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHAR_ARRAY[firstIndex])
        result.append(HEX_CHAR_ARRAY[secondIndex])
    }

    return result.toString()
}

fun String.hexStringToByteArray(): ByteArray {
    require(length % 2 == 0) { "Invalid hex string: length is not an even number" }

    val result = ByteArray(length / 2)

    for (i in indices step 2) {
        val firstIndex = HEX_CHARS.indexOf(this[i], ignoreCase = true)
        require(firstIndex != -1) { "Char ${this[i]} is not a hex digit" }

        val secondIndex = HEX_CHARS.indexOf(this[i + 1], ignoreCase = true)
        require(secondIndex != -1) { "Char ${this[i + 1]} is not a hex digit" }

        val octet = firstIndex.shl(4).or(secondIndex)
        result[i.shr(1)] = octet.toByte()
    }

    return result
}

class Bytes private constructor(private val bytes: ByteArray) {
    fun size() = bytes.size

    fun toByteArray() = bytes.copyOf()
    fun toHex() = bytes.toHex()

    override fun equals(other: Any?) = other === this || (other is Bytes && bytes.contentEquals(other.bytes))
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = bytes.toHex()

    companion object {
        fun of(bytes: ByteArray) = Bytes(bytes.copyOf())
    }
}

abstract class FixLenBytes(bytes: ByteArray) {
    private val bytes: ByteArray = let {
        val size = size()
        checkEquals(bytes.size, size) { "Wrong size: ${bytes.size} instead of $size" }
        bytes.copyOf()
    }

    abstract fun size(): Int

    fun toByteArray() = bytes.copyOf()
    fun toHex() = bytes.toHex()

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
