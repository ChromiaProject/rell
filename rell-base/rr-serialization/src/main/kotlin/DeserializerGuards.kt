/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

/**
 * Thrown when an [net.postchain.rell.base.model.rr.RR_App] FlatBuffer cannot be safely decoded: malformed bytes,
 * out-of-range indices, oversized vectors, excessive recursion, or any consistency violation.
 */
class RRDeserializationException(
    message: String,
    cause: Throwable? = null,
): IllegalStateException(message, cause)

/**
 * Defense-in-depth size caps for deserialization. The Java port of FlatBuffers
 * has no `Verifier` class (the C++ and Rust ports do), so the deserializer can't
 * structurally validate offsets. These constants instead bound the blast radius:
 * a malformed buffer claiming `fb.xxxLength = Int.MAX_VALUE` hits
 * [RRDeserializationException] rather than allocating 32 GB and OOMing the node.
 *
 * Legitimate programs are well under caps.
 */
internal object DeserLimits {
    const val MAX_BUFFER_SIZE: Int = 500_000_000 // 500 MB
    const val MAX_VECTOR_SIZE: Int = 10_000_000 // elements
    const val MAX_BYTE_ARRAY_SIZE: Int = 100_000_000 // elements
    const val MAX_GTV_SIZE: Int = 10_000_000 // 10 MB
    const val MAX_RECURSION_DEPTH: Int = 500
}

/**
 * Validates a length read from the wire before it drives an allocation or loop.
 * Returns the validated length.
 */
internal fun checkedVectorLength(length: Int, field: String): Int {
    if (length < 0) throw RRDeserializationException("$field: negative length $length")
    if (length > DeserLimits.MAX_VECTOR_SIZE) {
        throw RRDeserializationException("$field: length $length exceeds MAX_VECTOR_SIZE=${DeserLimits.MAX_VECTOR_SIZE}")
    }
    return length
}

/** Like [checkedVectorLength] but for raw byte fields with a larger cap. */
internal fun checkedByteArrayLength(length: Int, field: String): Int {
    if (length < 0) throw RRDeserializationException("$field: negative byte length $length")
    if (length > DeserLimits.MAX_BYTE_ARRAY_SIZE) {
        throw RRDeserializationException("$field: byte length $length exceeds MAX_BYTE_ARRAY_SIZE=${DeserLimits.MAX_BYTE_ARRAY_SIZE}")
    }
    return length
}

/**
 * Validates an index before it reaches a raw `list[idx]` access.
 * Accepts [Int] (already narrowed from a wire `UInt`) and the collection size.
 */
internal fun checkedIndex(index: Int, collectionSize: Int, field: String): Int {
    if (index !in 0..<collectionSize) {
        throw RRDeserializationException("$field: index $index out of range [0, $collectionSize)")
    }
    return index
}

/**
 * Narrows a wire `UInt` to an `Int` for use as a list index, rejecting values
 * ≥ 2^31 that would wrap to negative and bypass naive `>= 0` checks later on.
 */
internal fun checkedUIntAsIndex(u: UInt, collectionSize: Int, field: String): Int {
    if (u.toLong() >= collectionSize.toLong()) {
        throw RRDeserializationException("$field: wire index $u out of range [0, $collectionSize)")
    }
    return u.toInt()
}

/** [List.associateBy], but throws on duplicate keys instead of silently last-wins. */
internal inline fun <K, V> Iterable<V>.associateByFailOnDup(
    field: String,
    keySelector: (V) -> K,
): Map<K, V> {
    val result = LinkedHashMap<K, V>()
    for (v in this) {
        val k = keySelector(v)
        val prev = result.put(k, v)
        if (prev != null) {
            throw RRDeserializationException("$field: duplicate key $k")
        }
    }
    return result
}

/**
 * Recursion-depth guard for [deserializeExpr] / [deserializeStmt] / [deserializeType] /
 * [deserializeDbExpr]. A crafted buffer can nest expressions thousands of layers deep;
 * without this guard the JVM hits `StackOverflowError` and the process dies.
 *
 * Counter is thread-local because deserialization is typically single-threaded per
 * buffer; if two threads deserialize concurrently they get independent limits.
 * The counter naturally resets to zero when the outermost call unwinds (try/finally).
 */
private val deserializerDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

internal inline fun <T> withDeserializerDepth(block: () -> T): T {
    val d = deserializerDepth.get() + 1
    if (d > DeserLimits.MAX_RECURSION_DEPTH) {
        throw RRDeserializationException(
            "deserializer recursion depth exceeded ${DeserLimits.MAX_RECURSION_DEPTH}",
        )
    }
    deserializerDepth.set(d)
    try {
        return block()
    } finally {
        deserializerDepth.set(d - 1)
    }
}

/**
 * Bound for the per-buffer `objects` array, published for the duration of a single
 * `deserializeRellApp` call. Read by [checkedObjectDefIndex] to validate
 * `DbAtExpr.object_def_index` / `ObjectValueExpr.object_def_index` without having
 * to thread the bound through every inner deserializer signature.
 *
 * `-1` means "not inside a deserializeRellApp call": a direct caller of
 * [deserializeExpr] has no bound available, so any `object_def_index` they
 * encounter is rejected defensively.
 */
private val currentObjectsCount: ThreadLocal<Int> = ThreadLocal.withInitial { -1 }

internal inline fun <T> withObjectsCount(count: Int, block: () -> T): T {
    val prev = currentObjectsCount.get()
    currentObjectsCount.set(count)
    try {
        return block()
    } finally {
        currentObjectsCount.set(prev)
    }
}

/** Validates a signed wire `int` object-def index against the current buffer's object count. */
internal fun checkedObjectDefIndex(index: Int, field: String): Int {
    val count = currentObjectsCount.get()
    if (count < 0) {
        throw RRDeserializationException(
            "$field: object index encountered outside deserializeRellApp - bound unavailable",
        )
    }
    return checkedIndex(index, count, field)
}

/** Validates an unsigned wire `uint` object-def index and narrows to `Int`. */
internal fun checkedObjectDefIndex(index: UInt, field: String): Int {
    val count = currentObjectsCount.get()
    if (count < 0) {
        throw RRDeserializationException(
            "$field: object index encountered outside deserializeRellApp - bound unavailable",
        )
    }
    return checkedUIntAsIndex(index, count, field)
}

/**
 * Substitute for the FlatBuffers `Verifier` the Java port lacks: wraps the
 * outermost deserialize call so exceptions from walking a corrupted vtable
 * ([IndexOutOfBoundsException], [NullPointerException], [ClassCastException],
 * etc.) surface as [RRDeserializationException] instead of propagating wildly.
 */
internal inline fun <T> safeDeserialize(block: () -> T): T {
    return try {
        block()
    } catch (e: RRDeserializationException) {
        throw e
    } catch (e: StackOverflowError) {
        // Belt-and-braces for the withDeserializerDepth guard: if a code path
        // recurses without incrementing the counter, surface it as a normal
        // deserialization failure instead of killing the calling thread.
        throw RRDeserializationException("malformed buffer: stack overflow during deserialization", e)
    } catch (e: AssertionError) {
        // FlatBuffers' Kotlin codegen throws AssertionError on missing required
        // fields. Narrow to AssertionError so VM-fatal Errors still propagate.
        throw RRDeserializationException("malformed buffer: required field missing or assertion failed", e)
    } catch (e: RuntimeException) {
        throw RRDeserializationException(
            "malformed buffer: ${e.javaClass.simpleName} during deserialization",
            e,
        )
    }
}
