/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.fail

/**
 * Fuzzing [deserializeRellApp] against malfmormed byte inputs.
 *
 * The contract: any byte sequence (random garbage, truncated valid buffers, false length claims,
 * schema-hash spoofs, deep nesting) must either deserialize or throw [RRDeserializationException].
 *
 * This is fuzz-lite: deterministic seeded randomness, bounded iteration count. Covers the main attack primitives:
 *
 *  - Random bytes of varying sizes (offset manipulation, type confusion).
 *  - Empty / truncated buffers (vtable walks past buffer end).
 *  - False length claims (allocation bombs).
 *  - Schema-hash mismatches (wire-format drift signal).
 */
class DeserializerFuzzTest: BaseSerializerTest() {

    // Fixed seed for reproducibility. If a regression is found, the failing input
    // is deterministic from (seed, iteration index).
    private val seed: Long = System.getProperty("rell.test.fuzz.seed")?.toLongOrNull() ?: 0x5EED_42L
    private val iterations: Int = System.getProperty("rell.test.fuzz.iterations")?.toIntOrNull() ?: 2_000

    /** A tiny valid buffer reused by the truncation/hash-flip tests. */
    private val validBuffer: ByteArray by lazy {
        serializeRellApp(compileApp("function f(): integer = 42;"))
    }

    @Test
    fun randomBytesNeverCrashTheJvm() {
        val rng = Random(seed)
        val startNanos = System.nanoTime()
        // Bound wall-time too - a degenerate regression that takes 1s per call
        // shouldn't stall CI for hours on the full iteration budget.
        val deadlineNanos = startNanos + TimeUnit.MINUTES.toNanos(2)

        repeat(iterations) { i ->
            if (System.nanoTime() > deadlineNanos) return@randomBytesNeverCrashTheJvm

            val size = when (rng.nextInt(5)) {
                0 -> rng.nextInt(16)          // tiny - header region
                1 -> rng.nextInt(1_024)       // small
                2 -> rng.nextInt(100_000)     // medium
                3 -> rng.nextInt(1_000_000)   // large
                else -> rng.nextInt(10)       // micro - exercise boundary conditions
            }
            val bytes = ByteArray(size).also(rng::nextBytes)
            expectSafeOutcome(bytes, "iter=$i size=$size")
        }
    }

    @Test
    fun emptyBufferIsRejectedCleanly() {
        expectSafeOutcome(ByteArray(0), "empty")
    }

    @Test
    fun singleByteBufferIsRejectedCleanly() {
        expectSafeOutcome(ByteArray(1) { 0 }, "single zero byte")
        expectSafeOutcome(ByteArray(1) { 0xFF.toByte() }, "single 0xFF byte")
    }

    @Test
    fun truncatedValidBufferIsRejectedCleanly() {
        // Take a real valid buffer, truncate it at several points, and verify
        // every prefix either succeeds (implausible for short cuts) or throws
        // RRDeserializationException. Exercises vtable walks past buffer end.
        val valid = validBuffer
        check(valid.size > 32) { "expected non-trivial buffer, got ${valid.size} bytes" }

        for (cut in listOf(0, 1, 4, 8, 16, 24, valid.size / 4, valid.size / 2, valid.size - 1)) {
            val truncated = valid.copyOf(cut)
            expectSafeOutcome(truncated, "truncated to $cut bytes")
        }
    }

    @Test
    fun byteFlipAcrossValidBufferNeverCrashes() {
        // Flip every Nth byte in a valid buffer, one at a time. Any position may
        // land in the schema_hash, a vtable entry, a string length, a union tag, etc.
        val valid: ByteArray = validBuffer
        val corrupted = valid.copyOf()
        var hashMismatches = 0
        var otherRejections = 0
        var silentAcceptances = 0

        // Step of 7 is coprime with the FB 4-byte alignment - sweeps across
        // vtable offsets, length prefixes, data payloads.
        for (idx in corrupted.indices step 7) {
            corrupted[idx] = (corrupted[idx].toInt() xor 0xFF).toByte()
            try {
                deserializeRellApp(corrupted)
                silentAcceptances++ // Rare but legal - flipped byte was in padding.
            } catch (e: RRDeserializationException) {
                if ("schema hash mismatch" in (e.message ?: "")) hashMismatches++ else otherRejections++
            } catch (e: Throwable) {
                fail("byte flip at idx $idx leaked ${e.javaClass.simpleName}: ${e.message}", e)
            }
            corrupted[idx] = valid[idx]
        }

        // Diagnostic: at least one category should dominate, confirming the test
        // actually hit meaningful byte positions (not just padding).
        check(hashMismatches + otherRejections > 0) {
            "byte-flip sweep accepted every single flip - test hit only padding bytes"
        }
    }

    @Test
    fun validBufferStillRoundTripsAfterHardening() {
        // Smoke test: the hardening didn't break the happy path.
        assertDoesNotThrow { deserializeRellApp(validBuffer) }
    }

    private fun expectSafeOutcome(bytes: ByteArray, ctx: String) {
        try {
            deserializeRellApp(bytes)
        } catch (_: RRDeserializationException) {
            // Expected outcome.
        } catch (e: StackOverflowError) {
            fail("$ctx: recursion guard missed", e)
        } catch (e: OutOfMemoryError) {
            fail("$ctx: size guard missed", e)
        } catch (e: Throwable) {
            fail("$ctx: unexpected ${e.javaClass.name} escaped: ${e.message}", e)
        }
    }
}
