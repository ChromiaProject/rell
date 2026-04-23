// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_numeric_ops.cpp — extern "C" handle-based native numeric ops over rell::num (the
// faithful OpenJDK BigInteger/BigDecimal port + Rell semantics). Pure C++: the JIT'd code
// calls these instead of rell_sysfn_call, so decimal / big_integer arithmetic runs natively
// with ZERO JVM callback. See rell_numeric_ops.h for the ABI.

#include "rell_numeric_ops.h"

#include <exception>
#include <memory>
#include <string>
#include <vector>

#include "rell_decimal_math.h"

using rell::num::RellArithError;
using rell::num::RellBigDec;
using rell::num::RellBigInt;

namespace {

// Per-call pool: owns every handle/string handed to the JIT'd code, plus the deferred error.
// thread_local because the ORC JIT may dispatch on a worker thread; one logical call per thread
// at a time (the trampoline brackets each call with rell_num_pool_reset()).
struct Pool {
    std::vector<std::unique_ptr<RellBigDec>> decs;
    std::vector<std::unique_ptr<RellBigInt>> ints;
    std::vector<std::unique_ptr<std::string>> strs;
    bool pending = false;
    std::string code;
    std::string message;
};
thread_local Pool g_pool;

RellBigDec *adopt_dec(RellBigDec v) {
    g_pool.decs.push_back(std::make_unique<RellBigDec>(std::move(v)));
    return g_pool.decs.back().get();
}
RellBigInt *adopt_int(RellBigInt v) {
    g_pool.ints.push_back(std::make_unique<RellBigInt>(std::move(v)));
    return g_pool.ints.back().get();
}
const char *adopt_str(std::string s) {
    g_pool.strs.push_back(std::make_unique<std::string>(std::move(s)));
    return g_pool.strs.back()->c_str();
}

// Record the first error of the call; later errors are suppressed (the first is the faithful one,
// matching how the interpreter throws at the first failing op).
void set_error(const std::string &code, const std::string &message) {
    if (!g_pool.pending) {
        g_pool.pending = true;
        g_pool.code = code;
        g_pool.message = message;
    }
}
void set_error(const RellArithError &e) { set_error(e.code(), e.what()); }

RellBigDec &dec(void *h) { return *reinterpret_cast<RellBigDec *>(h); }
RellBigInt &big(void *h) { return *reinterpret_cast<RellBigInt *>(h); }

// Decimal binary-op wrapper: propagates a prior nullptr (deferred error), runs `f`, maps a thrown
// RellArithError into deferred state, and adopts the result into the pool.
template <class F>
void *dec_bin(void *a, void *b, F f) {
    if (a == nullptr || b == nullptr) return nullptr;
    try {
        return adopt_dec(f(dec(a), dec(b)));
    } catch (const RellArithError &e) {
        set_error(e);
    } catch (const std::exception &e) {
        set_error("", e.what());
    }
    return nullptr;
}

template <class F>
void *int_bin(void *a, void *b, F f) {
    if (a == nullptr || b == nullptr) return nullptr;
    try {
        return adopt_int(f(big(a), big(b)));
    } catch (const RellArithError &e) {
        set_error(e);
    } catch (const std::exception &e) {
        set_error("", e.what());
    }
    return nullptr;
}

}  // namespace

// =====================================================================================
// decimal
// =====================================================================================

extern "C" void *rell_num_decimal_parse(const char *s) {
    try {
        return adopt_dec(rell::num::decimal_parse(s == nullptr ? std::string() : std::string(s)));
    } catch (const RellArithError &e) {
        set_error(e);
    } catch (const std::exception &e) {
        set_error("decimal:invalid", e.what());
    }
    return nullptr;
}

extern "C" void *rell_num_decimal_add(void *a, void *b) { return dec_bin(a, b, rell::num::decimal_add); }
extern "C" void *rell_num_decimal_sub(void *a, void *b) { return dec_bin(a, b, rell::num::decimal_sub); }
extern "C" void *rell_num_decimal_mul(void *a, void *b) { return dec_bin(a, b, rell::num::decimal_mul); }
extern "C" void *rell_num_decimal_div(void *a, void *b) { return dec_bin(a, b, rell::num::decimal_div); }
extern "C" void *rell_num_decimal_rem(void *a, void *b) { return dec_bin(a, b, rell::num::decimal_rem); }

extern "C" const char *rell_num_decimal_to_string(void *h) {
    if (h == nullptr) return "";
    try {
        return adopt_str(rell::num::decimal_to_string(dec(h)));
    } catch (const std::exception &e) {
        set_error("", e.what());
    }
    return "";
}

// =====================================================================================
// big_integer
// =====================================================================================

extern "C" void *rell_num_bigint_parse(const char *s) {
    try {
        RellBigInt v = RellBigInt::fromDecimalString(s == nullptr ? std::string() : std::string(s));
        rell::num::bigint_range_check(v);  // throws bigint:overflow if out of range
        return adopt_int(std::move(v));
    } catch (const RellArithError &e) {
        set_error(e);
    } catch (const std::exception &e) {
        set_error("bigint:invalid", e.what());
    }
    return nullptr;
}

extern "C" void *rell_num_bigint_add(void *a, void *b) { return int_bin(a, b, rell::num::bigint_add); }
extern "C" void *rell_num_bigint_sub(void *a, void *b) { return int_bin(a, b, rell::num::bigint_sub); }
extern "C" void *rell_num_bigint_mul(void *a, void *b) { return int_bin(a, b, rell::num::bigint_mul); }
extern "C" void *rell_num_bigint_div(void *a, void *b) { return int_bin(a, b, rell::num::bigint_div); }
extern "C" void *rell_num_bigint_rem(void *a, void *b) { return int_bin(a, b, rell::num::bigint_rem); }

extern "C" const char *rell_num_bigint_to_string(void *h) {
    if (h == nullptr) return "";
    try {
        return adopt_str(big(h).toString());
    } catch (const std::exception &e) {
        set_error("", e.what());
    }
    return "";
}

// =====================================================================================
// deferred error + per-call pool
// =====================================================================================

extern "C" int rell_num_pending(void) { return g_pool.pending ? 1 : 0; }
extern "C" const char *rell_num_error_code(void) { return g_pool.code.c_str(); }
extern "C" const char *rell_num_error_message(void) { return g_pool.message.c_str(); }

extern "C" void rell_num_pool_reset(void) {
    g_pool.decs.clear();
    g_pool.ints.clear();
    g_pool.strs.clear();
    g_pool.pending = false;
    g_pool.code.clear();
    g_pool.message.clear();
}
