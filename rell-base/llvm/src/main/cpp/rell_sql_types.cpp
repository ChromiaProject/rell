// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_sql_types.cpp — namespace rell::sql: the Rell <-> PostgreSQL type adapter layer.
//
// Implements bind_param (RellValue -> libpq PQexecParams triplet) and decode_cell (one
// PGresult cell -> RellValue), mirroring the JVM sqlAdapter.toSql / sqlAdapter.fromSql bit
// for bit (rt_value_*.kt, rt_type_conversions.kt). See rell_sql_types.h for the full type
// table and the NULL-signalling contract.
//
// This TU is libpq-AWARE (it speaks PgOid / format ints) but does NOT include <libpq-fe.h>:
// Oid is uint32_t and the cell bytes are plain char buffers. The ONLY TU that includes
// <libpq-fe.h> is the PgExecutor .cpp, which consumes the PgParam triplets produced here.
//
// JNI/JVM coupling is kept OUT of the hot path and OUT of the inline-value path:
//   * Inline tags (BOOLEAN / INTEGER / ROWID / DEC_LONG / BIGINT_LONG) bind and decode with
//     ZERO JNI, using the native numerics (rell_bigint.h / rell_bigdec.h) for the NUMERIC
//     text round-trip. This keeps the golden round-trip tests buildable without libpq AND
//     without a live JVM.
//   * HANDLE-tagged binds (text / byte_array / json / gtv / entity ref, plus decimal/bigint
//     OUTSIDE the long-fitting envelope) and all HANDLE-producing decodes route through a
//     small set of OVERRIDABLE accessor hooks (RellSqlHooks). Production installs JVM-backed
//     implementations (reading the handle's Rt_Value via the value.cpp marshalling layer /
//     reboxing a decoded value back to a jobject); the no-JVM tests inject pure-C++ stubs or
//     feed pre-materialized inline values. The marshalling itself is NOT inlined here, exactly
//     as rell_sql_types.h prescribes ("add the JNI access behind a small accessor").

#include "rell_sql_types.h"

#include <cstring>

#include "rell_bigdec.h"
#include "rell_bigint.h"

namespace rell::sql {

using rell::num::RellBigDec;
using rell::num::RellBigInt;

// =====================================================================================
// Overridable JVM-access hooks.
//
// The string-gen and inline-value paths never touch these. They exist purely so the HANDLE
// cases (whose payload is an opaque jobject Rt_Value with no inline C++ form) can be
// materialized for a bind, and so decoded HANDLE results can be reboxed to a jobject — WITHOUT
// this TU depending on the JNI marshalling directly. Production wiring installs these from the
// executor/integration layer; unit tests install pure-C++ stubs.
//
// BIND hooks read an opaque handle (the jobject Rt_Value carried in RellValue.payload.handle)
// into materialized bytes; they return false if extraction failed (the caller then binds a
// NULL param so the executor's error path fires rather than a wrong value). DECODE hooks take
// the already-parsed primitive(s) and rebox to a RellValue (a HANDLE adopted into the call
// arena, or NONE on a JVM-side failure). A null hook means "not installed": the inline path is
// used where possible, otherwise NONE is returned.
//
// This struct intentionally lives in the .cpp (not rell_sql_types.h, which the integration must
// not have edited): it is the private contract between this adapter TU, the executor TU that
// installs the JVM-backed implementations, and the unit-test TU that installs C++ stubs. Both
// of those declare `extern` matching prototypes; the symbols are defined here.
// =====================================================================================

struct RellSqlHooks {
    // ---- bind side: handle -> bytes (return false on failure) ----
    bool (*entity_rowid)(jobject handle, int64_t *out) = nullptr;     // Rt_EntityValue.rowid
    bool (*enum_attr_value)(jobject handle, int32_t *out) = nullptr;  // Rt_RR_EnumValue.rrAttr.value
    bool (*handle_numeric_text)(jobject handle, std::string *out) =
        nullptr;  // out-of-envelope decimal/big_integer -> canonical NUMERIC text
    bool (*handle_text)(jobject handle, std::string *out) = nullptr;   // text / json UTF-8 payload
    bool (*handle_bytes)(jobject handle, std::string *out) = nullptr;  // byte_array / gtv octets

    // ---- decode side: primitive -> RellValue (HANDLE/arena-adopt, or NONE on failure) ----
    RellValue (*make_entity)(int64_t rowid) = nullptr;
    RellValue (*make_enum)(int32_t attrValue) = nullptr;          // bounds-checked JVM-side
    RellValue (*make_decimal_text)(const std::string &text) = nullptr;
    RellValue (*make_bigint_text)(const std::string &text) = nullptr;
    RellValue (*make_text)(std::string text) = nullptr;
    RellValue (*make_json)(std::string text) = nullptr;
    RellValue (*make_byte_array)(std::string bytes) = nullptr;
    RellValue (*make_gtv)(std::string bytes) = nullptr;
};

// The single installable hook table for this TU. Production installs JVM-backed function
// pointers; tests install pure-C++ stubs (or leave it default to exercise the inline paths).
RellSqlHooks g_rell_sql_hooks{};

void set_rell_sql_hooks(const RellSqlHooks &hooks) { g_rell_sql_hooks = hooks; }

namespace {

// ---- big-endian fixed-width integer packers (libpq BINARY format is network byte order) ----

std::string pack_be_int8(int64_t v) {
    uint64_t u = static_cast<uint64_t>(v);
    std::string out(8, '\0');
    for (int i = 0; i < 8; ++i) {
        out[7 - i] = static_cast<char>(u & 0xFFu);
        u >>= 8;
    }
    return out;
}

std::string pack_be_int4(int32_t v) {
    uint32_t u = static_cast<uint32_t>(v);
    std::string out(4, '\0');
    for (int i = 0; i < 4; ++i) {
        out[3 - i] = static_cast<char>(u & 0xFFu);
        u >>= 8;
    }
    return out;
}

int64_t unpack_be_int8(const char *data, int len) {
    // BINARY result: exactly 8 network-order bytes. (Defensive: shorter buffers are
    // sign-/zero-extended from the most-significant end, never read OOB.)
    uint64_t u = 0;
    for (int i = 0; i < len && i < 8; ++i) {
        u = (u << 8) | static_cast<uint8_t>(data[i]);
    }
    return static_cast<int64_t>(u);
}

int32_t unpack_be_int4(const char *data, int len) {
    uint32_t u = 0;
    for (int i = 0; i < len && i < 4; ++i) {
        u = (u << 8) | static_cast<uint8_t>(data[i]);
    }
    return static_cast<int32_t>(u);
}

// ---- text-format integer parse (PG INT8/INT4 come back as a decimal string in TEXT) ----
//
// Strict signed-decimal parse: optional leading '-'/'+', then ASCII digits. PG never pads or
// adds whitespace for INT result columns, so this is exact for the values PG returns.
int64_t parse_text_int(const char *data, int len) {
    int64_t sign = 1;
    int i = 0;
    if (len > 0 && (data[0] == '-' || data[0] == '+')) {
        if (data[0] == '-') sign = -1;
        i = 1;
    }
    int64_t acc = 0;
    for (; i < len; ++i) {
        char c = data[i];
        if (c < '0' || c > '9') break;
        acc = acc * 10 + (c - '0');
    }
    return sign * acc;
}

int64_t read_int8_cell(const char *data, int len, PgFormat fmt) {
    return fmt == PgFormat::BINARY ? unpack_be_int8(data, len) : parse_text_int(data, len);
}

int32_t read_int4_cell(const char *data, int len, PgFormat fmt) {
    return fmt == PgFormat::BINARY ? unpack_be_int4(data, len)
                                   : static_cast<int32_t>(parse_text_int(data, len));
}

// ---- BYTEA text decode: PG returns "\x<hex>" in TEXT result format ----
std::string decode_bytea_text(const char *data, int len) {
    std::string out;
    if (len >= 2 && data[0] == '\\' && (data[1] == 'x' || data[1] == 'X')) {
        out.reserve((len - 2) / 2);
        auto hexv = [](char c) -> int {
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'a' && c <= 'f') return c - 'a' + 10;
            if (c >= 'A' && c <= 'F') return c - 'A' + 10;
            return -1;
        };
        for (int i = 2; i + 1 < len; i += 2) {
            int hi = hexv(data[i]);
            int lo = hexv(data[i + 1]);
            if (hi < 0 || lo < 0) break;
            out.push_back(static_cast<char>((hi << 4) | lo));
        }
    } else {
        // Legacy/escape-format BYTEA or already-raw: copy verbatim. The executor requests
        // hex for BYTEA columns, so this branch is a defensive fallback only.
        out.assign(data, data + len);
    }
    return out;
}

// ---- inline NUMERIC text builders (no JNI), mirroring the JVM binds ----
//
// JVM decimal bind = setBigDecimal(value) -> PG NUMERIC. The faithful, lossless text form is
// BigDecimal.toPlainString(). For a DEC_LONG inline value the JVM source value is exactly
// BigDecimal.valueOf(mantissa, scale) (Tf_LongScaleDecimal.value), so RellBigDec::valueOf
// reproduces it bit-for-bit, and toPlainString() matches BigDecimal.toPlainString().
std::string dec_long_to_numeric_text(int64_t mantissa, int32_t scale) {
    return RellBigDec::valueOf(mantissa, scale).toPlainString();
}

// JVM big_integer bind = setBigDecimal(BigDecimal(value)) -> NUMERIC, scale 0. For an inline
// BIGINT_LONG that is just the integer's decimal string (no point, no exponent).
std::string bigint_long_to_numeric_text(int64_t v) {
    return RellBigInt::fromInt64(v).toString();
}

}  // namespace

// =====================================================================================
// pg_type_info — (oid, param format, result format) per RellSqlType.
//
// Param formats follow rell_sql_types.h's recommendation: int8/int4 BINARY (network order),
// boolean TEXT ("t"/"f"), decimal/big_integer TEXT (canonical NUMERIC string), text/json TEXT,
// bytea BINARY. Result formats: BINARY for the int columns (cheap exact decode) and BYTEA;
// TEXT for boolean/numeric/text/json. (The executor MAY override to a uniform TEXT result
// format; decode_cell branches on the per-cell `fmt` either way.)
// =====================================================================================
PgTypeInfo pg_type_info(RellSqlType t) {
    switch (t) {
        case RellSqlType::BOOLEAN:
            return {PgOid::BOOL, PgFormat::TEXT, PgFormat::TEXT};
        case RellSqlType::INTEGER:
        case RellSqlType::ROWID:
        case RellSqlType::ENTITY:
            return {PgOid::INT8, PgFormat::BINARY, PgFormat::BINARY};
        case RellSqlType::DECIMAL:
        case RellSqlType::BIG_INTEGER:
            return {PgOid::NUMERIC, PgFormat::TEXT, PgFormat::TEXT};
        case RellSqlType::TEXT:
            return {PgOid::TEXT, PgFormat::TEXT, PgFormat::TEXT};
        case RellSqlType::BYTE_ARRAY:
        case RellSqlType::GTV:
            return {PgOid::BYTEA, PgFormat::BINARY, PgFormat::BINARY};
        case RellSqlType::ENUM:
            return {PgOid::INT4, PgFormat::BINARY, PgFormat::BINARY};
        case RellSqlType::JSON:
            return {PgOid::JSON, PgFormat::TEXT, PgFormat::TEXT};
    }
    // Unreachable: every RellSqlType is handled above.
    return {PgOid::TEXT, PgFormat::TEXT, PgFormat::TEXT};
}

// =====================================================================================
// bind_param
// =====================================================================================
PgParam bind_param(const RellValue &value, RellSqlType col_type) {
    const PgTypeInfo info = pg_type_info(col_type);

    // A bound Rell `null` becomes a SQL NULL param: is_null=true, paramValues[i]==nullptr.
    // (The JVM binds the typed NULL via the same PreparedStatement slot; the OID still carries
    // the column type so PG infers the right NULL.)
    if (value.tag == RellTag::NULL_ || value.tag == RellTag::NONE) {
        return PgParam{info.oid, info.param_format, /*is_null=*/true, std::string()};
    }

    auto mk = [&](std::string bytes) {
        return PgParam{info.oid, info.param_format, /*is_null=*/false, std::move(bytes)};
    };

    switch (col_type) {
        case RellSqlType::BOOLEAN: {
            // setBoolean -> BOOL; PG text literal "t"/"f".
            bool b = value.payload.i64 != 0;
            return mk(std::string(b ? "t" : "f"));
        }
        case RellSqlType::INTEGER:
            // setLong -> INT8 (binary, big-endian).
            return mk(pack_be_int8(value.payload.i64));
        case RellSqlType::ROWID:
            // setLong(value) -> INT8. value >= 0 invariant already holds for ROWID.
            return mk(pack_be_int8(value.payload.i64));
        case RellSqlType::ENTITY: {
            // setLong(entity.rowid) -> INT8. Entity refs are always HANDLE (no inline form);
            // the rowid is read via the hook. (Defensive: an inline ROWID/INTEGER carrying the
            // rowid is also accepted.)
            if (value.tag == RellTag::HANDLE) {
                int64_t rowid = 0;
                if (g_rell_sql_hooks.entity_rowid != nullptr &&
                    g_rell_sql_hooks.entity_rowid(value.payload.handle, &rowid)) {
                    return mk(pack_be_int8(rowid));
                }
                // Hook missing/failed: cannot materialize; emit a NULL param so the caller's
                // ExceptionCheck / NONE handling fires rather than binding a wrong value.
                return PgParam{info.oid, info.param_format, /*is_null=*/true, std::string()};
            }
            return mk(pack_be_int8(value.payload.i64));
        }
        case RellSqlType::ENUM: {
            // setInt(rrAttr.value) -> INT4. Enums are HANDLE (Rt_RR_EnumValue); the attr int is
            // read via the hook. (Defensive: an inline INTEGER carrying the attr value works.)
            if (value.tag == RellTag::HANDLE) {
                int32_t attrValue = 0;
                if (g_rell_sql_hooks.enum_attr_value != nullptr &&
                    g_rell_sql_hooks.enum_attr_value(value.payload.handle, &attrValue)) {
                    return mk(pack_be_int4(attrValue));
                }
                return PgParam{info.oid, info.param_format, /*is_null=*/true, std::string()};
            }
            return mk(pack_be_int4(static_cast<int32_t>(value.payload.i64)));
        }
        case RellSqlType::DECIMAL: {
            // setBigDecimal(value) -> NUMERIC (text). Inline DEC_LONG: native build. HANDLE
            // (out-of-envelope Rt_DecimalValue): the canonical text comes from the hook (which
            // routes through the JVM's BigDecimal.toPlainString of the handle's value).
            if (value.tag == RellTag::DEC_LONG) {
                return mk(dec_long_to_numeric_text(value.payload.i64, value.scale));
            }
            if (value.tag == RellTag::HANDLE && g_rell_sql_hooks.handle_numeric_text != nullptr) {
                std::string text;
                if (g_rell_sql_hooks.handle_numeric_text(value.payload.handle, &text)) {
                    return mk(std::move(text));
                }
            }
            // FIDELITY: an INTEGER tag steered into a DECIMAL column (widening) binds the
            // integer value at scale 0 — matches BigDecimal(long) text.
            if (value.tag == RellTag::INTEGER || value.tag == RellTag::BIGINT_LONG) {
                return mk(bigint_long_to_numeric_text(value.payload.i64));
            }
            return PgParam{info.oid, info.param_format, /*is_null=*/true, std::string()};
        }
        case RellSqlType::BIG_INTEGER: {
            // setBigDecimal(BigDecimal(value)) -> NUMERIC (text), scale 0. Inline BIGINT_LONG:
            // native integer string. HANDLE (out-of-envelope): hook supplies the integer text.
            if (value.tag == RellTag::BIGINT_LONG || value.tag == RellTag::INTEGER) {
                return mk(bigint_long_to_numeric_text(value.payload.i64));
            }
            if (value.tag == RellTag::HANDLE && g_rell_sql_hooks.handle_numeric_text != nullptr) {
                std::string text;
                if (g_rell_sql_hooks.handle_numeric_text(value.payload.handle, &text)) {
                    return mk(std::move(text));
                }
            }
            return PgParam{info.oid, info.param_format, /*is_null=*/true, std::string()};
        }
        case RellSqlType::TEXT:
        case RellSqlType::JSON: {
            // setString / setObject(PGobject "json") -> TEXT/JSON payload (UTF-8). Text/json are
            // HANDLE (Rt_TextValue / Rt_JsonValue.str). The hook returns the UTF-8 bytes.
            if (value.tag == RellTag::HANDLE && g_rell_sql_hooks.handle_text != nullptr) {
                std::string text;
                if (g_rell_sql_hooks.handle_text(value.payload.handle, &text)) {
                    return mk(std::move(text));
                }
            }
            return PgParam{info.oid, info.param_format, /*is_null=*/true, std::string()};
        }
        case RellSqlType::BYTE_ARRAY:
        case RellSqlType::GTV: {
            // setBytes -> BYTEA (binary). byte_array = raw bytes; gtv = DER bytes. Both HANDLE;
            // the hook returns the raw octets.
            if (value.tag == RellTag::HANDLE && g_rell_sql_hooks.handle_bytes != nullptr) {
                std::string bytes;
                if (g_rell_sql_hooks.handle_bytes(value.payload.handle, &bytes)) {
                    return mk(std::move(bytes));
                }
            }
            return PgParam{info.oid, info.param_format, /*is_null=*/true, std::string()};
        }
    }
    // Unreachable.
    return PgParam{info.oid, info.param_format, /*is_null=*/true, std::string()};
}

// =====================================================================================
// decode_cell
// =====================================================================================
RellValue decode_cell(RellSqlType col_type, bool nullable, bool is_null, const char *data,
                      int len, PgFormat fmt) {
    // NULL handling (consensus-critical): PQgetisnull is authoritative and EXACTLY equals the
    // JVM's (sentinel && row.wasNull()) — see rell_sql_types.h. A nullable NULL -> Rell null;
    // a non-nullable NULL -> NONE sentinel so the executor raises sql_null:<type>
    // (Rt_SqlNull.check(name, nullable=false)).
    if (is_null) {
        return nullable ? rell::llvm_rt::rv_null() : rell::llvm_rt::rv_none();
    }

    switch (col_type) {
        case RellSqlType::BOOLEAN: {
            // getBoolean. TEXT: "t"/"f" (PG bool literal). BINARY: single byte 1/0.
            bool b;
            if (fmt == PgFormat::BINARY) {
                b = len > 0 && data[0] != 0;
            } else {
                b = len > 0 && (data[0] == 't' || data[0] == 'T' || data[0] == '1');
            }
            return rell::llvm_rt::rv_boolean(b);
        }
        case RellSqlType::INTEGER:
            return rell::llvm_rt::rv_integer(read_int8_cell(data, len, fmt));
        case RellSqlType::ROWID: {
            // getLong (>= 0). A genuine SQL NULL was already handled above; a stored 0 in a
            // NOT NULL rowid column never round-trips in practice (rowids start at 1).
            int64_t v = read_int8_cell(data, len, fmt);
            return rell::llvm_rt::rv_rowid(v);
        }
        case RellSqlType::ENTITY: {
            // Rt_EntityValue(type, rowid): a HANDLE. The rebox hook builds the jobject from the
            // rowid + the per-column entity type and adopts it into the call arena.
            int64_t rowid = read_int8_cell(data, len, fmt);
            if (g_rell_sql_hooks.make_entity != nullptr) {
                return g_rell_sql_hooks.make_entity(rowid);
            }
            return rell::llvm_rt::rv_none();
        }
        case RellSqlType::DECIMAL: {
            // getBigDecimal -> Rt_DecimalValue. NUMERIC arrives as canonical text. If it fits
            // the long-scale DEC_LONG envelope, decode inline (no JNI); else HANDLE via hook.
            std::string text(data, data + len);
            RellBigDec bd = RellBigDec::parse(text);
            int32_t scale = bd.scale();
            if (scale >= 0 && scale <= rell::llvm_rt::kDecLongMaxScale) {
                const RellBigInt &unscaled = bd.unscaledValue();
                if (unscaled.bitLength() < 64) {
                    int64_t mantissa = unscaled.longValueExact();
                    return rell::llvm_rt::rv_dec_long(mantissa, scale);
                }
            }
            // Out of the long envelope: rebox via the JVM (Rt_DecimalValue.get(BigDecimal)),
            // feeding the canonical NUMERIC text so the JVM applies its own canonicalization.
            if (g_rell_sql_hooks.make_decimal_text != nullptr) {
                return g_rell_sql_hooks.make_decimal_text(text);
            }
            return rell::llvm_rt::rv_none();
        }
        case RellSqlType::BIG_INTEGER: {
            // getBigDecimal -> toBigIntegerExact -> Rt_BigIntegerValue. NUMERIC text -> exact
            // integer. Inline if it fits i64, else HANDLE via hook.
            std::string text(data, data + len);
            RellBigDec bd = RellBigDec::parse(text);
            RellBigInt iv = bd.toBigInteger();  // exact: NUMERIC of a big_integer column is integral.
            if (iv.bitLength() < 64) {
                return rell::llvm_rt::rv_bigint_long(iv.longValueExact());
            }
            if (g_rell_sql_hooks.make_bigint_text != nullptr) {
                return g_rell_sql_hooks.make_bigint_text(iv.toString());
            }
            return rell::llvm_rt::rv_none();
        }
        case RellSqlType::TEXT: {
            // getString -> Rt_TextValue. UTF-8 bytes, verbatim.
            if (g_rell_sql_hooks.make_text != nullptr) {
                return g_rell_sql_hooks.make_text(std::string(data, data + len));
            }
            return rell::llvm_rt::rv_none();
        }
        case RellSqlType::JSON: {
            // getString -> parse -> Rt_JsonValue.
            if (g_rell_sql_hooks.make_json != nullptr) {
                return g_rell_sql_hooks.make_json(std::string(data, data + len));
            }
            return rell::llvm_rt::rv_none();
        }
        case RellSqlType::BYTE_ARRAY: {
            // getBytes -> Rt_ByteArrayValue. BINARY: raw octets. TEXT: "\x<hex>".
            std::string bytes =
                fmt == PgFormat::BINARY ? std::string(data, data + len) : decode_bytea_text(data, len);
            if (g_rell_sql_hooks.make_byte_array != nullptr) {
                return g_rell_sql_hooks.make_byte_array(std::move(bytes));
            }
            return rell::llvm_rt::rv_none();
        }
        case RellSqlType::GTV: {
            // getBytes -> decode -> Rt_GtvValue. Same BYTEA wire handling as byte_array.
            std::string bytes =
                fmt == PgFormat::BINARY ? std::string(data, data + len) : decode_bytea_text(data, len);
            if (g_rell_sql_hooks.make_gtv != nullptr) {
                return g_rell_sql_hooks.make_gtv(std::move(bytes));
            }
            return rell::llvm_rt::rv_none();
        }
        case RellSqlType::ENUM: {
            // getInt -> bounds-check 0 <= v < attrs.size -> Rt_RR_EnumValue(type, attrs[v]).
            // The bounds-check + attr lookup + type binding all live JVM-side; the hook is given
            // the raw int and returns the reboxed handle (or NONE on out-of-range, matching the
            // JVM's requireNotNull failure surfaced as an exception by the executor).
            int32_t v = read_int4_cell(data, len, fmt);
            if (g_rell_sql_hooks.make_enum != nullptr) {
                return g_rell_sql_hooks.make_enum(v);
            }
            return rell::llvm_rt::rv_none();
        }
    }
    // Unreachable.
    return rell::llvm_rt::rv_none();
}

}  // namespace rell::sql
