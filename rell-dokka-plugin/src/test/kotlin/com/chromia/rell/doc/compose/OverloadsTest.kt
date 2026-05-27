/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.compose

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.chromia.rell.doc.model.*
import org.junit.jupiter.api.Test

class OverloadsTest {

    private fun fn(name: String, vararg paramTypes: String, kind: Doc_FunctionKind = Doc_FunctionKind.FUNCTION) =
        Doc_Function(
            name = name,
            qname = "main.$name",
            docMd = "",
            source = null,
            deprecated = null,
            kind = kind,
            params = paramTypes.map { Doc_Param(it, Doc_Type.Named(it, null), "") },
            returnType = null,
            typeParams = emptyList(),
        )

    private fun prop(name: String) = Doc_Property(
        name = name, qname = "main.$name", docMd = "", source = null, deprecated = null,
        type = Doc_Type.Named("integer", null),
    )

    @Test
    fun `same name and kind merge into primary plus overloads`() {
        val a = fn("sub", "i")
        val b = fn("sub", "i", "j")
        val c = fn("sub", "i", "j", "k")
        val out = groupFunctionOverloads(listOf(a, b, c))
        assertThat(out).hasSize(1)
        val primary = out[0] as Doc_Function
        assertThat(primary.name).isEqualTo("sub")
        assertThat(primary.params.map { it.name }).containsExactly("i")
        assertThat(primary.overloads).hasSize(2)
        assertThat(primary.overloads[0].params.map { it.name }).containsExactly("i", "j")
        assertThat(primary.overloads[1].params.map { it.name }).containsExactly("i", "j", "k")
    }

    @Test
    fun `different kinds do not merge`() {
        val f = fn("foo", kind = Doc_FunctionKind.FUNCTION)
        val q = fn("foo", kind = Doc_FunctionKind.QUERY)
        val out = groupFunctionOverloads(listOf(f, q))
        assertThat(out).hasSize(2)
    }

    @Test
    fun `non-function defs pass through`() {
        val p = prop("x")
        val f1 = fn("y")
        val f2 = fn("y", "i")
        val out = groupFunctionOverloads(listOf(p, f1, f2))
        assertThat(out).hasSize(2)
        assertThat(out[0]).isEqualTo(p)
        val primary = out[1] as Doc_Function
        assertThat(primary.overloads).hasSize(1)
    }

    @Test
    fun `empty and single-element lists are returned unchanged`() {
        assertThat(groupFunctionOverloads(emptyList())).hasSize(0)
        val single = listOf(fn("only"))
        assertThat(groupFunctionOverloads(single)).hasSize(1)
    }

    @Test
    fun `build order is preserved across non-overload defs`() {
        val a = prop("a")
        val f1 = fn("z")
        val b = prop("b")
        val f2 = fn("z", "i")
        val out = groupFunctionOverloads(listOf(a, f1, b, f2))
        // a, z (primary with one overload), b
        assertThat(out).hasSize(3)
        assertThat(out[0]).isEqualTo(a)
        assertThat((out[1] as Doc_Function).name).isEqualTo("z")
        assertThat((out[1] as Doc_Function).overloads).hasSize(1)
        assertThat(out[2]).isEqualTo(b)
    }
}
