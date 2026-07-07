/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

/**
 * Regression tests for named arguments on stdlib functions that read parameters by declaration
 * index (`join_to_text`). Two bugs are covered:
 *
 *  - a named argument that reorders parameters was ignored on member calls (the argument permutation
 *    was not applied), so `join_to_text(prefix = '<', separator = '*')` produced `*1<2<3`;
 *  - a named argument that skips an earlier optional formed a gap that misplaced the value into the
 *    skipped slot — silently wrong for `prefix = '<'`, and a `ClassCastException` for `transform`.
 */
class LibNamedArgGapTest: BaseRellTest() {
    // No gap, no reorder: baseline.
    @Test fun testDeclarationOrder() {
        chk("[1, 2, 3].join_to_text(separator = '*', prefix = '<')", "text[<1*2*3]")
    }

    // Reorder without a gap: the argument permutation must be applied on member calls.
    @Test fun testReorderedNamedArgs() {
        chk("[1, 2, 3].join_to_text(prefix = '<', separator = '*')", "text[<1*2*3]")
    }

    // A single named argument that binds the first optional: no gap.
    @Test fun testSingleLeadingOptional() {
        chk("[1, 2, 3].join_to_text(separator = '_')", "text[1_2_3]")
    }

    // Gap: `separator` is skipped, `prefix` named -> `separator` keeps its default `', '`.
    @Test fun testGapSkipsLeadingOptional() {
        chk("[1, 2, 3].join_to_text(prefix = '<')", "text[<1, 2, 3]")
    }

    // Gap that previously crashed: only `transform` named, all earlier optionals skipped.
    @Test fun testGapWithTransform() {
        def("function tr(x: integer): text = 'n' + x;")
        chk("[1, 2, 3].join_to_text(transform = tr(*))", "text[n1, n2, n3]")
    }

    // Fully positional form is unaffected.
    @Test fun testPositional() {
        def("function tr(x: integer): text = 'n' + x;")
        chk("[1, 2, 3].join_to_text('*', '<', '>', null, '...', tr(*))", "text[<n1*n2*n3>]")
    }
}
