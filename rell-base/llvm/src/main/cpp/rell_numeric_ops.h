// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_numeric_ops.h — the extern "C" handle-based native numeric ops the JIT'd code calls
// INSTEAD of bouncing to the JVM via rell_sysfn_call. Every op here is pure C++ (rell::num,
// the faithful OpenJDK BigInteger/BigDecimal port + Rell semantics layer); NO JNI callback
// happens during computation. This is the layer that makes decimal / big_integer arithmetic
// run natively in JIT'd code without re-entering the JVM.
//
// ABI for the JIT:
//   * Operands and results are OPAQUE handles (RellBigDec*/RellBigInt*), passed as `void*`.
//   * Handles are owned by a thread_local per-call POOL; the JIT entry trampoline calls
//     rell_num_pool_reset() once the call's result has been read out, freeing everything.
//   * Errors are deferred: an op that hits a Rell arithmetic error (overflow, /0, invalid
//     parse) records it in thread_local state and returns nullptr/"" ; subsequent ops on a
//     nullptr propagate (return nullptr) so the JIT'd code can do ONE check at the end via
//     rell_num_pending(). rell_num_error_code()/_message() carry the exact Rell code/message.
//
// The host process exports these as extern "C" symbols; the LLJIT host-process search
// generator (jni_bridge.cpp) binds them at JIT lookup time, same path as rell_sysfn_call.

#ifndef RELL_NUMERIC_OPS_H
#define RELL_NUMERIC_OPS_H

extern "C" {

// ---- decimal (Rt_DecimalValue / Lib_DecimalMath semantics) --------------------------------
void *rell_num_decimal_parse(const char *s);     // canonical-normalized; nullptr+err on invalid
void *rell_num_decimal_add(void *a, void *b);
void *rell_num_decimal_sub(void *a, void *b);
void *rell_num_decimal_mul(void *a, void *b);
void *rell_num_decimal_div(void *a, void *b);     // scale 20, HALF_UP; /0 -> err
void *rell_num_decimal_rem(void *a, void *b);
const char *rell_num_decimal_to_string(void *h);  // Rell stripped form ("2.5", "7", "0")

// ---- big_integer (Rt_BigIntegerValue / Lib_BigIntegerMath semantics) ----------------------
void *rell_num_bigint_parse(const char *s);       // range-checked; nullptr+err on invalid/oob
void *rell_num_bigint_add(void *a, void *b);
void *rell_num_bigint_sub(void *a, void *b);
void *rell_num_bigint_mul(void *a, void *b);
void *rell_num_bigint_div(void *a, void *b);       // truncate toward zero; /0 -> err
void *rell_num_bigint_rem(void *a, void *b);
const char *rell_num_bigint_to_string(void *h);

// ---- deferred error + per-call pool -------------------------------------------------------
int rell_num_pending(void);              // 1 iff an op errored since the last reset.
const char *rell_num_error_code(void);   // exact Rell code, e.g. "decimal:overflow". "" if none.
const char *rell_num_error_message(void);
void rell_num_pool_reset(void);          // free all pooled handles+strings, clear error state.

}  // extern "C"

#endif  // RELL_NUMERIC_OPS_H
