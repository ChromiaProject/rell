/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_FunctionDefinition
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_ByteArrayValue
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NATIVE crypto hashing (sha256 / keccak256) wired into the JIT, replacing the rell_sysfn_call
 * back-call for those two functions with native calls into rell_crypto.cpp (self-contained FIPS
 * 180-4 SHA-256 + Keccak-256 with 0x01 pad — NO OpenSSL, NO libsecp256k1). byte_array is an arena
 * BYTEARRAY carrier (value.cpp), so the hash reads the operand buffer and writes a 32-byte result
 * buffer end-to-end native.
 *
 * Bit-exactness vs the interpreter (lib_crypto.kt):
 *   * crypto.sha256 / byte_array.sha256 == MessageDigest("SHA-256") — FIPS 180-4. Asserted against
 *     the NIST empty / "abc" vectors.
 *   * crypto.keccak256 == BouncyCastle Keccak.Digest256 — ORIGINAL Keccak, padding suffix 0x01 (NOT
 *     SHA3-256's 0x06). Asserted against the Ethereum empty-input vector
 *     c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470.
 *
 * Each hash test pins jitHits>0 / jitMisses==0 (the native path was taken) AND that the result
 * equals the known digest. The NEGATIVE tests pin that the signing family (get_signature /
 * verify_signature / eth_ecrecover) STILL routes through the JVM (jitMisses>=1) and returns the
 * correct value — proving signing was NOT wrongly wired native (its byte-identity is unproven; see
 * REVIEW_stdlib_ports.md H2). Mirrors [Llvm_ByteArrayCoverageTest].
 */
class Llvm_CryptoHashCoverageTest {

    // Known digests (lib_crypto.kt / rell_crypto.cpp self-test vectors).
    private val sha256Empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val sha256Abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val keccak256Empty = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
    private val keccak256Abc = "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"

    @Test
    fun `crypto sha256 of a known vector is native and bit-exact`() {
        // x"616263" == "abc". crypto.sha256 hashes natively (rell_crypto_sha256) into a fresh 32-byte
        // arena byte_array; equals MessageDigest("SHA-256").digest("abc").
        val (backend, exeCtx) = setup("function f(b: byte_array): byte_array = crypto.sha256(b);")
        val fn = backend.fn("f")

        assertEquals(ba(sha256Abc), backend.callFunction(fn, exeCtx, listOf(ba(0x61, 0x62, 0x63))))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `crypto sha256 of empty is native and bit-exact`() {
        val (backend, exeCtx) = setup("function f(): byte_array = crypto.sha256(x\"\");")
        val fn = backend.fn("f")

        assertEquals(ba(sha256Empty), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `byte_array sha256 member is native and bit-exact`() {
        // The byte_array `.sha256()` member shares lib_crypto.kt's Sha256 body — same native path
        // (the receiver is the lone operand). Previously soft-failed (see Llvm_ByteArrayCoverageTest
        // to_hex); now JIT-resident.
        val (backend, exeCtx) = setup("function f(b: byte_array): byte_array = b.sha256();")
        val fn = backend.fn("f")

        assertEquals(ba(sha256Abc), backend.callFunction(fn, exeCtx, listOf(ba(0x61, 0x62, 0x63))))
        assertEquals(ba(sha256Empty), backend.callFunction(fn, exeCtx, listOf(ba())))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `crypto keccak256 of empty is native and bit-exact (Ethereum vector)`() {
        // keccak256("") == c5d2460186f7233c... — the Ethereum empty-input digest. A SHA3-256 (0x06
        // pad) would produce a DIFFERENT value, so this pins the 0x01 Keccak padding.
        val (backend, exeCtx) = setup("function f(): byte_array = crypto.keccak256(x\"\");")
        val fn = backend.fn("f")

        assertEquals(ba(keccak256Empty), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `crypto keccak256 of a known vector is native and bit-exact`() {
        val (backend, exeCtx) = setup("function f(b: byte_array): byte_array = crypto.keccak256(b);")
        val fn = backend.fn("f")

        assertEquals(ba(keccak256Abc), backend.callFunction(fn, exeCtx, listOf(ba(0x61, 0x62, 0x63))))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `sha256 inside a larger JIT'd function is native and bit-exact`() {
        // The hash is one node in a larger body (native concat feeding a native hash, whose result is
        // returned). The whole function stays on the JIT path: jitMisses==0.
        val (backend, exeCtx) = setup(
            "function f(a: byte_array, b: byte_array): byte_array = crypto.sha256(a + b);"
        )
        val fn = backend.fn("f")

        // sha256("abc") via x"6162" + x"63".
        assertEquals(ba(sha256Abc), backend.callFunction(fn, exeCtx, listOf(ba(0x61, 0x62), ba(0x63))))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `get_signature still back-calls and verify_signature confirms it (interpreter)`() {
        // NEGATIVE: signing is NOT wired native (byte-identity unproven; libsecp256k1 not linked). It
        // must route through the JVM (rell_sysfn_call), which soft-fails the function today (jitMisses
        // >= 1). Correctness proof without a hardcoded 64-byte signature: the signature produced for
        // (data_hash, privkey) verifies against the matching pubkey (a true round-trip), and both the
        // sign and the verify functions soft-fail to the interpreter.
        val (backend, exeCtx) = setup(
            "function sign(h: byte_array, k: byte_array): byte_array = crypto.get_signature(h, k);\n" +
                "function pub(k: byte_array): byte_array = crypto.privkey_to_pubkey(k, true);\n" +
                "function vrf(h: byte_array, p: byte_array, s: byte_array): boolean =" +
                " crypto.verify_signature(h, p, s);"
        )
        val privkey = ba(*IntArray(32) { 0x11 })
        val dataHash = ba(sha256Abc)  // any valid 32-byte hash.
        val sig = backend.callFunction(backend.fn("sign"), exeCtx, listOf(dataHash, privkey))
        val pub33 = backend.callFunction(backend.fn("pub"), exeCtx, listOf(privkey))

        // The signature is 64 bytes (r||s) and verifies against the keypair's pubkey.
        assertEquals(64, (sig as Rt_ByteArrayValue).value.size)
        assertEquals(true, callBool(backend, exeCtx, backend.fn("vrf"), listOf(dataHash, pub33, sig)))
        // Determinism: RFC6979 — signing twice yields identical bytes.
        assertEquals(sig, backend.callFunction(backend.fn("sign"), exeCtx, listOf(dataHash, privkey)))

        // Every signing/verifying call routed through the interpreter — none took the native path.
        assertEquals(0, backend.jitHits)
        assertTrue(backend.jitMisses >= 1, "get_signature/verify_signature must soft-fail to the interpreter")
    }

    @Test
    fun `eth_ecrecover still back-calls and returns the correct address (interpreter)`() {
        // NEGATIVE: eth_ecrecover stays on the back-call. Vector from lib_crypto.kt's doc comment:
        // keccak256(eth_ecrecover(r, s, v-27, h)).sub(12) == address 5b0c0875...675b1614. The
        // eth_ecrecover node soft-fails the whole function (jitMisses >= 1), so the interpreter
        // computes the full chain — and the result is the known Ethereum address.
        val (backend, exeCtx) = setup(
            "function f(r: byte_array, s: byte_array, v: integer, h: byte_array): byte_array =" +
                " crypto.keccak256(crypto.eth_ecrecover(r, s, v, h)).sub(12);"
        )
        val fn = backend.fn("f")

        val r = ba("cf722a47bcf1da61967ccc6405e31db4d37bce153255a6937e5cceb222caead0")
        val s = ba("cf722a47bcf1da61967ccc6405e31db4d37bce153255a6937e5cceb222caead0")
        val h = ba("53d7b11e61a8059aa4bc3248d24b2936436c9796dfe7f18e414c181004f79427")
        val recId = Rt_IntValue.get((0x1c - 27).toLong())
        val args = listOf(r, s, recId, h)

        assertEquals(ba("5b0c087542d5c1e66df0041e179c4201675b1614"), backend.callFunction(fn, exeCtx, args))
        // The eth_ecrecover back-call soft-failed the whole function; the interpreter ran it.
        assertEquals(0, backend.jitHits)
        assertTrue(backend.jitMisses >= 1, "eth_ecrecover must soft-fail to the interpreter")
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun Llvm_Backend.fn(name: String): RR_FunctionDefinition =
        rrApp.module(ModuleName.EMPTY)!!.functions[name]!!

    private fun ba(vararg bytes: Int): Rt_Value =
        Rt_ByteArrayValue.get(ByteArray(bytes.size) { bytes[it].toByte() })

    private fun ba(hex: String): Rt_Value =
        Rt_ByteArrayValue.get(ByteArray(hex.length / 2) {
            hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        })

    private fun callBool(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: RR_FunctionDefinition,
        args: List<Rt_Value>,
    ): Boolean = (backend.callFunction(fn, exeCtx, args) as Rt_BooleanValue).value

    private fun setup(code: String): Pair<Llvm_Backend, Rt_ExecutionContext> {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        check(cRes.errors.isEmpty()) { "Compilation errors: ${cRes.errors.map { it.code }}" }
        val rrApp = checkNotNull(cRes.rrApp) { "compileApp returned null RR_App" }

        val backend = Llvm_Backend.forCompilation(rrApp, cRes.compilationSysFns)

        val globalCtx = Rt_GlobalContext(
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
            Rt_NopPrinter,
            Rt_NopPrinter,
            typeCheck = false,
        )

        val appCtx = Rt_AppContext(globalCtx, Rt_ChainContext.NULL, backend)
        val sqlCtx = Rt_NullSqlContext.create(rrApp.sqlDefs)
        val exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)
        return backend to exeCtx
    }
}
