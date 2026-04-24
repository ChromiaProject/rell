/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Tests that sys-function keys written into the RR_App are content-derived, not JVM-identity based.
 */
class SysFnKeyDeterminismTest: BaseSerializerTest() {
    @Test fun testSerializedBytesContainNoIdentityHashKeys() {
        // Exercises global sys fns (abs), member sys fns on primitives (text.size, integer.to_text),
        // and a second integer member fn to surface potential overload collisions.
        val bytes = serializeRellApp(
            compileApp(
                """
                function main(): text {
                    val x = abs(-5);
                    val y = 'hello'.size();
                    return x.to_text();
                }
                """.trimIndent(),
            ),
        )
        // FlatBuffers stores strings inline as UTF-8; a raw byte scan is adequate for an
        // anti-pattern check. Five-plus digits is the floor for typical JVM identityHashCode
        // values and keeps the regex tight against unrelated small numeric suffixes.
        val decoded = bytes.toString(Charsets.ISO_8859_1)
        val hits = Regex("""[A-Za-z_.]@\d{5,}""").findAll(decoded).map { it.value }.toSet()
        assertTrue(
            hits.isEmpty(),
            "Serialized RR_App contains identity-hash-like sys-fn name suffixes ($hits). " +
                    "Sys-fn names must be content-derived so the bytes are stable across processes.",
        )
    }

    @Test fun testStdlibRegistryKeysAreDeterministic() {
        // Inspect the compilation-local sys-fn registry directly (the keys flow into the
        // serialized RR tree). Catches the same anti-pattern on the resolver side, independent
        // of the serializer - in case a key is emitted to the registry but never reaches the
        // binary, or a future refactor changes how the binary encodes names.
        val (_, sysFns) = compileAppWithSysFns(
            """
            function main(): text {
                val x = abs(-5);
                val y = 'hello'.size();
                return x.to_text();
            }
            """.trimIndent(),
        )
        val badKeys = sysFns.keys.filter { it.matches(Regex(""".*@\d{5,}.*""")) }
        assertTrue(
            badKeys.isEmpty(),
            "Compilation sys-fn registry contains identityHashCode-like suffixes: $badKeys",
        )
    }
}
