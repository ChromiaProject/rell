/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Positive boundary tests for the deserialization safety guards in [DeserializerGuards].
 * The fuzz tests cover "anything goes in, only [RRDeserializationException] comes out".
 * These pin the diagnostic message and rejection path for each *named* guard, so a future
 * refactor that accidentally weakens (or moves) a check fails loudly rather than silently.
 *
 *  - Schema-hash mismatch (App.schema_hash vs FBS_SCHEMA_HASH).
 *  - Buffer size cap (DeserLimits.MAX_BUFFER_SIZE).
 *  - Recursion depth cap (DeserLimits.MAX_RECURSION_DEPTH) via a deeply nested expression.
 *
 *  Per-vector / per-byte-array caps and `associateByFailOnDup` (duplicate mount names) require
 *  crafting raw FlatBuffer bytes by hand and aren't unit-testable from a Rell source program.
 */
class DeserializerSafetyBoundaryTest: BaseSerializerTest() {
    @Test
    fun bufferLargerThanMaxBufferSizeIsRejected() {
        val oversized = ByteArray(DeserLimits.MAX_BUFFER_SIZE + 1)
        val ex = assertFails { deserializeRellApp(oversized) }
        assertTrue(ex is RRDeserializationException, "Expected RRDeserializationException, got ${ex::class}")
        assertContains(ex.message ?: "", "MAX_BUFFER_SIZE")
    }

    @Test
    fun schemaHashMismatchProducesNamedError() {
        val valid = serializeRellApp(compileApp("function f(): integer = 42;"))

        // Locate the schema hash bytes: they're 32 bytes (SHA-256) and appear verbatim in the
        // serialized buffer. Flip one byte and re-deserialize.
        val hashStart = checkNotNull(locateSchemaHash(valid)) { "FBS_SCHEMA_HASH not found in serialized buffer" }
        val tampered = valid.copyOf().also {
            it[hashStart] = (it[hashStart].toInt() xor 0xFF).toByte()
        }

        val ex = assertFails { deserializeRellApp(tampered) }
        assertTrue(ex is RRDeserializationException, "Expected RRDeserializationException, got ${ex::class}")
        assertContains(ex.message ?: "", "schema hash mismatch")
    }

    @Test
    fun deeplyNestedExpressionExceedsRecursionDepth() {
        // White-box: drive `withDeserializerDepth` past its threshold directly.
        // The fuzz suite covers wire-level paths; here we just pin the named guard's
        // rejection message. Going through compile + serialize would force a 500-deep
        // AST through the compiler, which overflows CI's default 512 KiB thread stack
        // before ever reaching the deserializer.
        fun recurse(remaining: Int): Unit = withDeserializerDepth {
            if (remaining > 0) recurse(remaining - 1)
        }

        val ex = assertFails { recurse(DeserLimits.MAX_RECURSION_DEPTH + 1) }
        assertTrue(ex is RRDeserializationException, "Expected RRDeserializationException, got ${ex::class}")
        assertContains(ex.message ?: "", "recursion depth")
    }

    /**
     * Locate FBS_SCHEMA_HASH in the serialized buffer. The schema hash is written as a vector of
     * ubytes; we look for an exact byte run match. Returns the start offset, or null if absent.
     */
    private fun locateSchemaHash(buffer: ByteArray): Int? {
        val needle = FBS_SCHEMA_HASH
        outer@ for (i in 0..buffer.size - needle.size) {
            for (j in needle.indices) {
                if (buffer[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
    }
}
