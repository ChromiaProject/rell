/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

/**
 * Pins downstream-discovered runtime regressions in the RR interpreter that aren't covered
 * by lang/ feature tests because they fire only via the chr-test path (test-tx wrapping,
 * SQL bind on R-driven enum values, structural-conversion arg passing).
 *
 * Each method names the surface that originally surfaced the bug — keep new entries narrow
 * and one-failure-per-test so future regressions show up against the right surface.
 */
class RuntimeFnCallRegressionTest : BaseRellTest() {
    /**
     * Regression: `Rt_InterpreterImpl.setParams` NPE'd on `args[i].type.rrType!!` when an
     * argument's `Rt_ValueClass` was a capability-only implementer (companion of a generic
     * container type — `rrType` defaults to null). The runtime check was redundant with
     * the compiler's static type-check; removed entirely. Symptom in ft4-lib /
     * directory-chain / postchain-eif: `chr test` crashed on the first function call whose
     * argument was a test-infra wrapper (`rell.test.op`, `rell.test.tx`, …).
     */
    @Test fun testFunctionCallAcceptsTestOpArg() {
        file("probe.rell", """
            @test module;
            function sink(op: rell.test.op) {}
            @test function probe() {
                sink(rell.test.op('foo', (1).to_gtv()));
                assert_equals(true, true);
            }
        """)

        chkTests("probe", "probe=OK")
    }

    /**
     * Regression: enum values returned by `R_EnumDefinition.rtValues()` were typed via
     * `rTypeStub` → `Rt_GenericRrType`, which had no `sqlAdapter`. Any SQL bind of an
     * enum-typed value blew up at `SqlArgs.bind` (rt_sql_builder.kt:84) with
     * `No SQL adapter for type: <enum>`. Reproduced in cardtradeplatform's
     * `marketplace:OfferType` and ft4-lib's `transfer_type`.
     */
    @Test fun testEnumValueBindsAsSqlParam() {
        tstCtx.useSql = true
        def("enum kind { K_A, K_B, K_C }")
        def("entity row { name; k: kind; }")
        // The bind happens on the parameter `k = kind.K_B`. Pre-fix this threw
        // "No SQL adapter for type: kind" mid-insert.
        chkOp("create row('first', kind.K_B);")
        chk("row @* {} ( _=.name, _=.k )", "list<(text,kind)>[(text[first],kind[K_B])]")
        // And on the filter side, where the enum sits inside a where-clause bind.
        chk("row @? { .k == kind.K_B } ( _=.name )", "text[first]")
        chk("row @? { .k == kind.K_A } ( _=.name )", "null")
    }
}

class StructGtvRegressionTest : BaseRellTest() {
    /**
     * Repro for the cardtradeplatform/ft4-lib downstream failure: calling `.to_gtv()` on a
     * struct whose first attribute is itself a struct (with a leading `byte_array` field) blew
     * up with `Rt_HeapStruct cannot be cast to Rt_ByteArrayValue` inside
     * `rell.gtv_ext(...).to_gtv`. The cast fired because the outer struct's gtv conversion
     * called the byte_array converter on the inner struct value.
     */
    @Test fun testNestedStructWithByteArrayFirstFieldToGtv() {
        def("struct Inner { id: byte_array; tag: text; }")
        def("struct Outer { inner: Inner; tag: text; }")
        // Pre-fix: toGtv'd misapply the byte_array converter to the inner struct value and
        // throw ClassCastException(Rt_HeapStruct → Rt_ByteArrayValue).
        chk("Outer(inner = Inner(id = x'deadbeef', tag = 'ok'), tag = 'outer').to_gtv().to_bytes().size() > 0", "boolean[true]")
    }

    /**
     * Repro for the ft4-lib downstream failure: serializing a  (a lib
     * struct whose first attribute is , itself starting with a
     *  blockchain_rid) blew up with  inside .
     */
    @Test fun testGtxTransactionToGtv() {
        chk("""
            gtx_transaction(
                body = gtx_transaction_body(blockchain_rid = x'aa', operations = [], signers = []),
                signatures = []
            ).to_gtv().to_bytes().size() > 0
        """.trimIndent(), "boolean[true]")
    }

    /**
     * Repro variant — the ft4 code uses a parameter declared as just the type
     * (`function iccf_proof_for(..., gtx_transaction, ...)` → implicit name = type name).
     * Inside, `gtx_transaction.to_gtv()` may resolve through a different path than the
     * literal expression form. Exercise that path directly.
     */
    @Test fun testGtxTransactionImplicitNameParamToGtv() {
        file("helper.rell", """
            module;
            function tx_hash_size(gtx_transaction): integer = gtx_transaction.to_gtv().to_bytes().size();
        """)
        def("import helper;")
        chk("""
            helper.tx_hash_size(
                gtx_transaction(
                    body = gtx_transaction_body(blockchain_rid = x'aa', operations = [], signers = []),
                    signatures = []
                )
            ) > 0
        """.trimIndent(), "boolean[true]")
    }

    /**
     * Closer to the ft4 path: operations list populated via `struct<op>.to_gtx_operation()`
     * conversions, then the enclosing gtx_transaction serialised. The conversion lives in
     * lib_opcontext.kt and builds an Rt_StructValue against the gtx_operation lib struct;
     * if that materialised struct has the wrong attribute layout, the recursive
     * gtv-conversion misroutes a byte_array converter onto a struct field.
     */
    @Test fun testGtxTransactionWithStructOpConversions() {
        file("helpers.rell", """
            module;
            operation my_op(x: integer) {}
        """)
        def("import helpers;")
        chk("""
            gtx_transaction(
                body = gtx_transaction_body(
                    blockchain_rid = x'aa',
                    operations = [struct<helpers.my_op>(x = 7).to_gtx_operation()],
                    signers = []
                ),
                signatures = [x''.to_gtv()]
            ).to_gtv().to_bytes().size() > 0
        """.trimIndent(), "boolean[true]")
    }

    /**
     * Repro for the stale-rrDefIndex regression: the lib struct `gtx_transaction` is a
     * singleton R_Struct shared across compilations, and its `var rrDefIndex` got
     * re-written every time a fresh rrApp registered it. Downstream call sites that
     * captured `selfR = R_StructType(gtx_transaction)` at compile time then resolved to
     * whatever struct happened to occupy that slot in the *previous* rrApp, producing
     * `Rt_HeapStruct cannot be cast to Rt_ByteArrayValue` when the gtv-conversion of the
     * mis-pinned struct hit a byte_array attribute.
     *
     * Exercises a round-trip of the gtv-conversion: serialise gtx_transaction to gtv bytes,
     * decode back, compare. The fix lives in `Rt_InterpreterImpl.rrTypeOfR` for
     * R_StructType / R_VirtualStructType, validating the cached `rrDefIndex` against
     * `rrApp.allStructs` and falling back to a name lookup when stale.
     */
    @Test fun testGtxTransactionRoundTripsAfterStaleIndex() {
        chk("""
            gtx_transaction.from_bytes(
                gtx_transaction(
                    body = gtx_transaction_body(blockchain_rid = x'aa', operations = [], signers = []),
                    signatures = []
                ).to_bytes()
            ).body.blockchain_rid
        """.trimIndent(), "byte_array[aa]")
    }
}
