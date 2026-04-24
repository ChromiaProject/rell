/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import com.google.flatbuffers.FlatBufferBuilder

/**
 * Runs [block] with the FlatBufferBuilder's default-elision disabled, then restores it.
 *
 * flatc's Kotlin codegen emits `builder.addInt(slot, value, 0)` for every scalar
 * add-method. The builder skips the write whenever `value == 0`, which is the FB
 * on-wire default for all scalar types. For **optional scalar fields** (`field: T = null`),
 * elision collapses "present with value 0" into the wire signal for "absent", so the
 * reader returns `null` that breaks the round-trip when the actual value is `0`.
 *
 * A global `forceDefaults(true)` would fix the scalar case, but it also forces
 * offset-field writes (vectors, tables, strings), where `0` is the legitimate "null"
 * sentinel the reader expects - turning empty vectors into buffer-overrun reads.
 *
 * So we toggle force-defaults **only around optional scalar adds**. Non-optional scalars
 * and all offset-typed fields (tables, vectors, strings) retain flatc's standard
 * elision semantics.
 */
internal inline fun FlatBufferBuilder.forcedScalar(block: () -> Unit) {
    forceDefaults(true)
    try {
        block()
    } finally {
        forceDefaults(false)
    }
}
