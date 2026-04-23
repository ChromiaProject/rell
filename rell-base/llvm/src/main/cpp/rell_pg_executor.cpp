// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_pg_executor.cpp — namespace rell::sql: the libpq execution layer for the native Rell SQL
// backend. This is the ONLY translation unit that includes <libpq-fe.h>; it consumes a RellSql
// ({ text with $1..$N positional placeholders, ordered RellValue binds } produced by SqlGen) and
// runs it against a PostgreSQL connection through PQexecParams.
//
// PORT TARGET (the ground-truth Kotlin):
//   - ConnectionSqlExecutor.executeQuery  (rell-base/runtime-core/.../sql/sql.kt) — the SELECT
//     loop: prepare, bind each param 1-based, iterate the ResultSet, decode each column.
//   - SqlSelectRt.execute                 (.../runtime/rt_sql_builder.kt) — decode rows column by
//     column via sqlAdapter.fromSql(rsRow, i+1, nullable).
//   - ParameterizedSql binding            (.../runtime/rt_sql_builder.kt) — adapter.rtToSql(params,
//     i+1, value); binds are 1-based positionally, in render order (== RellSql.binds order).
//   - executeUpdateSqlInner / executeDeleteSqlInner / buildInsertSql — the DML paths ending in
//     RETURNING, whose decoded rows we surface as DmlResult.returning.
//
// FIDELITY (consensus-critical): the SQL text + bind order are already byte-identical to
// DbSqlGen's rendered output (SqlGen's job); this file must preserve that by binding the params
// positionally to $1..$N in RellSql.binds order and decoding result columns left-to-right per the
// caller-supplied result types. NULL signalling mirrors Rt_SqlNull.check exactly: PQgetisnull is
// authoritative (== the JVM's ResultSet.wasNull()), and a non-nullable NULL is a hard error.

#include "rell_pg_executor.h"

#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include <libpq-fe.h>

#include "rell_runtime.h"
#include "rell_sql.h"
#include "rell_sql_types.h"

namespace rell::sql {

namespace {

// Build a human-readable PgError message from a connection-level failure (PQerrorMessage carries
// the libpq diagnostic; no SQLSTATE is available without a PGresult).
[[noreturn]] void throw_conn_error(PGconn *conn, const char *what) {
    std::string msg = what;
    if (conn != nullptr) {
        const char *pg = PQerrorMessage(conn);
        if (pg != nullptr && pg[0] != '\0') {
            msg += ": ";
            msg += pg;
        }
    }
    throw PgError(msg);
}

// Build a PgError from a failed PGresult, threading the SQLSTATE through the message so the JNI
// boundary can map it to an Rt_Exception (the JDBC path surfaces SQLException.getSQLState()
// identically). The PGresult is PQclear-ed before throwing.
[[noreturn]] void throw_result_error(PGconn *conn, PGresult *res, const char *what) {
    std::string msg = what;
    // SQLSTATE first (stable, machine-parsable), then the primary message.
    const char *sqlstate = res != nullptr ? PQresultErrorField(res, PG_DIAG_SQLSTATE) : nullptr;
    if (sqlstate != nullptr && sqlstate[0] != '\0') {
        msg += " [SQLSTATE ";
        msg += sqlstate;
        msg += "]";
    }
    const char *detail = res != nullptr ? PQresultErrorMessage(res) : nullptr;
    if ((detail == nullptr || detail[0] == '\0') && conn != nullptr) {
        detail = PQerrorMessage(conn);
    }
    if (detail != nullptr && detail[0] != '\0') {
        msg += ": ";
        msg += detail;
    }
    if (res != nullptr) {
        PQclear(res);
    }
    throw PgError(msg);
}

// Infer the bind's RellSqlType from a RellValue tag alone.
//
// FIDELITY / consensus note: this is a fallback. RellSql.binds carries only RellValues, but the
// bind's true static type is known to SqlGen at render time (the DbExpr that produced the bind, or
// the entity-attribute type for INSERT/UPDATE). The tag→type map below is EXACT for the inline
// lattice (BOOLEAN/INTEGER/ROWID and the long-fitting DEC_LONG/BIGINT_LONG), and for every HANDLE
// it routes through bind_param's JVM-marshalling path, which re-derives the concrete Rt_Value
// class. The only genuinely ambiguous cases are:
//   - a HANDLE that is decimal vs big_integer vs text vs byte_array vs json vs gtv vs entity vs
//     enum — bind_param disambiguates by inspecting the wrapped Rt_Value, so HANDLE is forwarded
//     as a neutral marker (the production bind_param ignores the hint for HANDLEs).
//   - an INTEGER tag bound into a NUMERIC/ROWID column — here the tag says INTEGER, which is the
//     correct PG bind shape (INT8) for both integer and rowid (rowid is INT8 too); decimal/bigint
//     columns only ever receive DEC_LONG/BIGINT_LONG/HANDLE binds, never a bare INTEGER tag.
// TODO(port): thread a parallel `std::vector<RellSqlType>` alongside RellSql.binds from SqlGen so
// the executor binds with the resolved column type instead of inferring. Until then this is the
// faithful best effort; bind_param is the disambiguation backstop for HANDLEs.
RellSqlType infer_bind_type(const RellValue &v) {
    switch (v.tag) {
        case RellTag::BOOLEAN:
            return RellSqlType::BOOLEAN;
        case RellTag::INTEGER:
            return RellSqlType::INTEGER;
        case RellTag::ROWID:
            return RellSqlType::ROWID;
        case RellTag::DEC_LONG:
            return RellSqlType::DECIMAL;
        case RellTag::BIGINT_LONG:
            return RellSqlType::BIG_INTEGER;
        case RellTag::NULL_:
            // A bound Rell null: the PG type does not matter for a NULL param (PQexecParams gets a
            // null pointer); bind_param yields is_null=true. INTEGER is a harmless placeholder OID.
            return RellSqlType::INTEGER;
        case RellTag::HANDLE:
        default:
            // bind_param inspects the wrapped Rt_Value to pick the real OID/format for a HANDLE;
            // the hint is not load-bearing here. Use TEXT as the neutral forward marker.
            return RellSqlType::TEXT;
    }
}

// Decode every column of result row `row` (0-based) into `out`, per `col_types`/`nullable`.
// `out` is cleared and refilled. Mirrors SqlSelectRt.execute's per-column fromSql loop, but with
// PQgetisnull as the authoritative NULL test (== ResultSet.wasNull()) — see rell_sql_types.h.
void decode_row(PGresult *res, int row, const std::vector<RellSqlType> &col_types,
                const std::vector<bool> &nullable, std::vector<RellValue> &out) {
    const int ncols = static_cast<int>(col_types.size());
    out.clear();
    out.reserve(ncols);
    for (int col = 0; col < ncols; ++col) {
        const bool is_null = PQgetisnull(res, row, col) == 1;
        // PQgetvalue returns "" (not nullptr) for SQL NULL; decode_cell guards on is_null first.
        const char *data = PQgetvalue(res, row, col);
        const int len = PQgetlength(res, row, col);
        // The executor requests TEXT results uniformly (see exec_params); decode_cell branches on
        // the format it is told. FIDELITY: keep this in lockstep with exec_params' resultFormat.
        const PgFormat fmt = PgFormat::TEXT;
        const bool nullable_col = col < static_cast<int>(nullable.size()) ? nullable[col] : false;
        out.push_back(decode_cell(col_types[col], nullable_col, is_null, data, len, fmt));
    }
}

}  // namespace

// =====================================================================================
// Construction / destruction
// =====================================================================================

PgExecutor::PgExecutor(const std::string &conninfo) {
    conn_ = PQconnectdb(conninfo.c_str());
    if (conn_ == nullptr) {
        throw PgError("PQconnectdb returned null (out of memory)");
    }
    if (PQstatus(conn_) != CONNECTION_OK) {
        // Capture the message before PQfinish frees the conn object.
        std::string msg = "PostgreSQL connection failed";
        const char *pg = PQerrorMessage(conn_);
        if (pg != nullptr && pg[0] != '\0') {
            msg += ": ";
            msg += pg;
        }
        PQfinish(conn_);
        conn_ = nullptr;
        throw PgError(msg);
    }
    owns_conn_ = true;
}

PgExecutor::PgExecutor(PGconn *borrowed_conn) : conn_(borrowed_conn), owns_conn_(false) {
    if (conn_ == nullptr) {
        throw PgError("PgExecutor: borrowed connection is null");
    }
}

PgExecutor::~PgExecutor() {
    if (owns_conn_ && conn_ != nullptr) {
        PQfinish(conn_);
    }
    conn_ = nullptr;
}

// =====================================================================================
// exec_params — the shared PQexecParams driver.
// =====================================================================================

PGresult *PgExecutor::exec_params(const RellSql &sql) {
    const int nparams = static_cast<int>(sql.binds.size());

    // Materialize each RellValue bind into a PgParam (owning its serialized bytes), then build the
    // parallel arrays PQexecParams wants. The PgParams must outlive the PQexecParams call, hence
    // the separate vector kept alive on the stack.
    std::vector<PgParam> params;
    params.reserve(nparams);
    for (const RellValue &v : sql.binds) {
        // FIDELITY: RellSql.binds carries only the RellValue, not its resolved RellSqlType. The
        // bind's static type is known to SqlGen at render time; until RellSql threads a parallel
        // per-bind RellSqlType vector, infer it from the value tag here. This is exact for the
        // tags that map 1:1 (BOOLEAN/INTEGER/ROWID/DEC_LONG/BIGINT_LONG) and routes every HANDLE
        // (text/byte_array/decimal/bigint-overflow/json/gtv/entity/enum) through bind_param's
        // JVM-marshalling path, which itself disambiguates the concrete Rt_Value class.
        // TODO(port): give RellSql a parallel `std::vector<RellSqlType> bind_types` so rowid-vs-
        // integer and decimal-vs-bigint binds are steered by the column type rather than the tag.
        params.push_back(bind_param(v, infer_bind_type(v)));
    }

    std::vector<Oid> param_types(nparams);
    std::vector<const char *> param_values(nparams);
    std::vector<int> param_lengths(nparams);
    std::vector<int> param_formats(nparams);

    for (int i = 0; i < nparams; ++i) {
        const PgParam &p = params[i];
        param_types[i] = static_cast<Oid>(p.type);
        if (p.is_null) {
            // A SQL NULL param MUST be passed as a null pointer; length/format are ignored.
            param_values[i] = nullptr;
            param_lengths[i] = 0;
            param_formats[i] = 0;
        } else {
            param_values[i] = p.data.data();
            param_lengths[i] = p.length();
            param_formats[i] = static_cast<int>(p.format);
        }
    }

    // resultFormat = 0 (TEXT) uniformly: PQexecParams takes ONE result format for the whole
    // result, and per-column formats are not expressible here. decode_cell parses the TEXT form
    // for every type (BYTEA arrives as the "\\x..." hex escape, which decode_cell handles). This
    // matches the JDBC adapters' getString/getLong/getBigDecimal/getBytes decode shape.
    PGresult *res = PQexecParams(conn_, sql.text.c_str(), nparams,
                                 nparams > 0 ? param_types.data() : nullptr,
                                 nparams > 0 ? param_values.data() : nullptr,
                                 nparams > 0 ? param_lengths.data() : nullptr,
                                 nparams > 0 ? param_formats.data() : nullptr,
                                 /*resultFormat=*/0);
    if (res == nullptr) {
        throw_conn_error(conn_, "PQexecParams returned null");
    }

    const ExecStatusType status = PQresultStatus(res);
    if (status != PGRES_COMMAND_OK && status != PGRES_TUPLES_OK) {
        throw_result_error(conn_, res, "PostgreSQL query failed");
    }
    return res;
}

// =====================================================================================
// exec_select — run a SELECT and stream decoded rows to the callback.
// =====================================================================================

void PgExecutor::exec_select(const RellSql &sql, const std::vector<RellSqlType> &col_types,
                             const std::vector<bool> &nullable, const RowCallback &cb) {
    PGresult *res = exec_params(sql);
    // Reuse one row buffer; decode_row clears/refills it, the callback consumes it within the call
    // (the contract: the vector is valid only during the callback).
    std::vector<RellValue> row;
    const int nrows = PQntuples(res);
    try {
        for (int r = 0; r < nrows; ++r) {
            decode_row(res, r, col_types, nullable, row);
            cb(row);
        }
    } catch (...) {
        PQclear(res);
        throw;
    }
    PQclear(res);
}

// =====================================================================================
// exec_dml — run an INSERT/UPDATE/DELETE ... RETURNING and collect affected count + rows.
// =====================================================================================

PgExecutor::DmlResult PgExecutor::exec_dml(const RellSql &sql,
                                           const std::vector<RellSqlType> &returning_types,
                                           const std::vector<bool> &returning_nullable) {
    PGresult *res = exec_params(sql);
    DmlResult out;
    try {
        const int nrows = PQntuples(res);

        // `affected` mirrors the JDBC update count. For a RETURNING query libpq reports TUPLES_OK
        // with the returned rows; PQcmdTuples is the authoritative affected-row string (it is the
        // count for INSERT/UPDATE/DELETE, including the RETURNING variants). Fall back to the row
        // count when PQcmdTuples is empty.
        const char *tuples = PQcmdTuples(res);
        if (tuples != nullptr && tuples[0] != '\0') {
            out.affected = std::atoi(tuples);
        } else {
            out.affected = nrows;
        }

        // Decode RETURNING rows only when the caller described the columns (a snapshot/returning
        // path). An empty returning_types means "affected count only" (matches the JVM paths that
        // don't read RETURNING back).
        if (!returning_types.empty()) {
            out.returning.reserve(nrows);
            std::vector<RellValue> row;
            for (int r = 0; r < nrows; ++r) {
                decode_row(res, r, returning_types, returning_nullable, row);
                out.returning.push_back(row);
            }
        }
    } catch (...) {
        PQclear(res);
        throw;
    }
    PQclear(res);
    return out;
}

// =====================================================================================
// Transactions — parameterless BEGIN/COMMIT/ROLLBACK.
// =====================================================================================

void PgExecutor::exec_simple(const char *command) {
    PGresult *res = PQexec(conn_, command);
    if (res == nullptr) {
        throw_conn_error(conn_, "PQexec returned null");
    }
    const ExecStatusType status = PQresultStatus(res);
    if (status != PGRES_COMMAND_OK && status != PGRES_TUPLES_OK) {
        throw_result_error(conn_, res, command);
    }
    PQclear(res);
}

void PgExecutor::begin() { exec_simple("BEGIN"); }
void PgExecutor::commit() { exec_simple("COMMIT"); }
void PgExecutor::rollback() { exec_simple("ROLLBACK"); }

}  // namespace rell::sql
