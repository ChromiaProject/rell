// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_sql_types.h — namespace rell::sql: the Rell <-> PostgreSQL type adapter layer for the
// native SQL executor. Two directions, mirroring the JVM sqlAdapter.toSql / sqlAdapter.fromSql
// (rt_value_*.kt, rt_type_conversions.kt) bit-for-bit:
//
//   bind_param(value, ...)  — produce the libpq PQexecParams triplet (paramValue text/binary,
//                             paramLength, paramFormat) + paramType OID for one RellValue.
//   decode_cell(...)        — read one PGresult cell back into a RellValue per the column's Rell
//                             type, honoring the JVM's NULL conventions.
//
// This header declares the adapter API and the per-type OID/format table. It is libpq-aware in
// the sense that it speaks Oid/format ints, but it does NOT include <libpq-fe.h> — Oid is just
// uint32_t and the byte buffers are std::string/std::vector<char>. The ONLY translation unit
// that includes <libpq-fe.h> is the PgExecutor .cpp; it consumes these triplets. (This keeps the
// adapter logic and its golden round-trip tests buildable without libpq if desired.)
//
// =====================================================================================
// TYPE MAPPING (Rell type → PG type / bind / read) — from the JVM sqlAdapters
// =====================================================================================
//   boolean      BOOL    (16)  bind setBoolean        read getBoolean   NULL: PQgetisnull
//   integer      INT8    (20)  bind setLong           read getLong      NULL: PQgetisnull
//   rowid        INT8    (20)  bind setLong (>=0)      read getLong      NULL sentinel: 0 → null
//   entity ref   INT8    (20)  bind setLong (rowid)    read getLong      NULL sentinel: 0 → null
//   decimal      NUMERIC (1700) bind setBigDecimal     read getBigDecimal NULL: PQgetisnull
//   big_integer  NUMERIC (1700) bind setBigDecimal(toBigDecimal) read getBigDecimal → exact int
//   text         TEXT    (25)  bind setString         read getString    NULL: PQgetisnull
//   byte_array   BYTEA   (17)  bind setBytes          read getBytes     NULL: PQgetisnull
//   enum         INT4    (23)  bind setInt(attr.value) read getInt       NULL sentinel: 0 → null
//   json         JSON    (114) bind setObject(PGobject json) read getString → parse
//   gtv          BYTEA   (17)  bind setBytes          read getBytes
//
// NULL SIGNALLING (consensus-critical, mirrors Rt_SqlNull.check):
//   - The JVM reads via JDBC getLong/getInt (which return 0 for SQL NULL) THEN checks a
//     per-type sentinel: rowid/integer/entity → `v == 0`, enum → `v == 0`, boolean → `!v`,
//     combined with ResultSet.wasNull(). On the native side we have a CLEANER signal:
//     PQgetisnull(res, row, col) is authoritative for SQL NULL. decode_cell uses PQgetisnull as
//     the primary NULL test; the legacy "0 == NULL" sentinels are NOT needed for correctness of
//     a true SQL NULL, but ARE preserved in the result shape: a non-nullable column that is
//     genuinely NULL throws (Rt_SqlNull.check with nullable=false), and a nullable column that
//     is NULL yields the Rell null value. FIDELITY: a column holding an actual stored 0 rowid is
//     NOT NULL at the SQL level, so PQgetisnull=false and we return rowid 0 — matching the JVM,
//     because the JVM's `v==0 → wasNull` only triggers when the DB value is truly NULL (a stored
//     0 in a NOT NULL column never round-trips as a Rell value in practice; rowids start at 1).
//
// BINARY vs TEXT format: PQexecParams supports per-param format (0=text, 1=binary) and a
// resultFormat. The faithful, simplest choice that matches JDBC's setXxx semantics is:
//   - integer/rowid/entity: BINARY int8 (network byte order) OR text decimal — both accepted by
//     PG for INT8; the port uses BINARY (8-byte big-endian) to avoid locale/format ambiguity.
//   - boolean: TEXT "t"/"f" (or BINARY single byte 1/0).
//   - decimal/big_integer: TEXT — the canonical decimal string (BigDecimal.toPlainString-style,
//     unscaled with explicit scale); NUMERIC binary wire format is intricate, TEXT is exact.
//   - text/json: TEXT (UTF-8 bytes).
//   - byte_array/gtv: BINARY (raw bytes, format 1) — equivalent to setBytes → BYTEA.
// decode reads results in TEXT format for NUMERIC/INT (parse) and BINARY for BYTEA; the executor
// picks a per-column result format. See PgParam::format below.
//
// FIDELITY: the decimal/big_integer string MUST be the exact value PG stores; for big_integer
// the JVM binds BigDecimal(value) (scale 0). decode parses NUMERIC text → exact integer
// (toBigIntegerExact) for big_integer, or the full decimal for decimal. The native bigdec/bigint
// parse/format lives in rell_bigdec.h / rell_bigint.h and is reused here (NOT reimplemented).

#ifndef RELL_SQL_TYPES_H
#define RELL_SQL_TYPES_H

#include <cstdint>
#include <string>
#include <vector>

#include "rell_runtime.h"

namespace rell::sql {

using rell::llvm_rt::RellValue;
using rell::llvm_rt::RellTag;

// PostgreSQL type OIDs used as paramTypes for PQexecParams. (pg_type.dat canonical OIDs.)
enum class PgOid : uint32_t {
    BOOL = 16,
    BYTEA = 17,
    INT8 = 20,
    INT4 = 23,
    TEXT = 25,
    JSON = 114,
    NUMERIC = 1700,
};

// Wire format for a single param/result cell. 0 == text, 1 == binary (libpq convention).
enum class PgFormat : int { TEXT = 0, BINARY = 1 };

// The Rell logical column type that drives bind/decode. It is NOT the FlatBuffer RR_Type as-is;
// the SqlGen/executor resolves an at-expression result type (or entity-attribute type) down to
// one of these leaves. Composite/opaque Rell types are not directly SQL-stored (they decompose
// into entity refs / primitives), so the SQL layer only ever sees these.
enum class RellSqlType {
    BOOLEAN,      // BOOL
    INTEGER,      // INT8
    ROWID,        // INT8, 0==null sentinel
    ENTITY,       // INT8 (rowid), 0==null sentinel
    DECIMAL,      // NUMERIC
    BIG_INTEGER,  // NUMERIC (integral)
    TEXT,         // TEXT
    BYTE_ARRAY,   // BYTEA
    ENUM,         // INT4, 0==null sentinel
    JSON,         // JSON
    GTV,          // BYTEA
};

// One PQexecParams parameter, owning its serialized bytes. The executor builds the parallel
// paramValues[]/paramLengths[]/paramFormats[]/paramTypes[] arrays from a vector of these.
//
// For a SQL NULL param (a bound Rell null), `is_null` is true and paramValues[i] must be passed
// as nullptr to PQexecParams (length/format ignored). Otherwise `data` holds the raw bytes
// (text: UTF-8 without NUL terminator significance — length is explicit; binary: raw octets).
struct PgParam {
    PgOid type;
    PgFormat format;
    bool is_null;
    std::string data;  // serialized value bytes (text or binary); empty + is_null for NULL.

    int length() const { return static_cast<int>(data.size()); }
};

// =====================================================================================
// bind_param — RellValue → PgParam, per the TYPE MAPPING above.
//
// `col_type` is the resolved Rell column/param type (the at-expression bind's static type, known
// from the DbExpr node that produced the bind, or the entity attribute type for INSERT/UPDATE).
// It disambiguates the inline tags (an INTEGER tag bound into a NUMERIC column, an entity rowid,
// an enum's int, etc.). For most binds the RellValue tag alone suffices, but rowid-vs-integer
// and decimal-vs-bigint must be steered by `col_type`.
//
// For HANDLE-tagged values (text, byte_array, decimal/bigint outside the long envelope, json,
// gtv), the bytes are materialized by routing through the JVM marshalling (from the handle's
// Rt_Value) — the production impl reads the handle via to_jvm/sqlAdapter.toSqlValue; the
// no-JVM test impl is fed pre-materialized values. The port should add the JNI access behind a
// small accessor rather than inlining marshalling here.
// =====================================================================================
PgParam bind_param(const RellValue &value, RellSqlType col_type);

// =====================================================================================
// decode_cell — one PGresult cell → RellValue, per `col_type`.
//
// `is_null` is PQgetisnull(res,row,col). `data`/`len` are PQgetvalue/PQgetlength bytes in the
// result format the executor requested for this column (`fmt`). `nullable` tells whether the
// Rell column type is nullable: when the cell is NULL and !nullable, the JVM throws
// (Rt_SqlNull.check(name,false)); decode_cell signals that via a NONE-tagged RellValue + the
// executor raising, OR returns rv_null() when nullable. (The exact error path is the executor's;
// decode_cell returns rv_null() for a nullable NULL and a NONE sentinel for a non-nullable NULL.)
//
// Decoding rules (mirror fromSql):
//   BOOLEAN     parse "t"/"f" (text) or byte (binary)            → rv_boolean
//   INTEGER     parse int8                                       → rv_integer
//   ROWID       parse int8 (>=0)                                 → rv_rowid
//   ENTITY      parse int8 rowid → HANDLE Rt_EntityValue(type,rowid) [needs JVM rebox]
//   DECIMAL     parse NUMERIC text → DEC_LONG if long-fitting else HANDLE Rt_DecimalValue
//   BIG_INTEGER parse NUMERIC text → exact integer → BIGINT_LONG if fits else HANDLE
//   TEXT        UTF-8 bytes                                      → HANDLE Rt_TextValue
//   BYTE_ARRAY  raw bytes                                        → HANDLE Rt_ByteArrayValue
//   ENUM        parse int4 → HANDLE Rt_RR_EnumValue(type, attrs[v]) [needs JVM rebox + bounds]
//   JSON        UTF-8 text → parse                               → HANDLE Rt_JsonValue
//   GTV         raw bytes → decode                               → HANDLE Rt_GtvValue
//
// The HANDLE results require a JVM rebox (the value class has no inline C++ form). decode_cell
// takes the per-column rebox context (a small callback / the column's resolved Rt_ValueClass
// handle) so it can adopt the produced jobject into the call arena. For the no-DB string-gen
// tests this header is not exercised; decode_cell is only linked into the executor TU.
// =====================================================================================
RellValue decode_cell(RellSqlType col_type, bool nullable, bool is_null, const char *data, int len,
                      PgFormat fmt);

// Maps a RellSqlType to its (oid, preferred param format, preferred result format). Defined in
// the .cpp; the executor uses it to fill paramTypes[] and to choose PQexecParams resultFormat
// per column. Centralizes the table so bind/decode and the executor agree.
struct PgTypeInfo {
    PgOid oid;
    PgFormat param_format;
    PgFormat result_format;
};
PgTypeInfo pg_type_info(RellSqlType t);

}  // namespace rell::sql

#endif  // RELL_SQL_TYPES_H
