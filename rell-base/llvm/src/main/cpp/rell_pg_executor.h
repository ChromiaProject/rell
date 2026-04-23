// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_pg_executor.h — namespace rell::sql: the thin libpq execution layer for the native Rell
// SQL backend. It takes a RellSql (raw $1..$N PostgreSQL + ordered RellValue binds, produced by
// SqlGen) and runs it against a PostgreSQL connection via PQexecParams, binding each RellValue
// through the rell_sql_types.h adapters and decoding result rows back into RellValues.
//
// This is the ONLY header/TU in the SQL layer that includes <libpq-fe.h> (so SQL string
// generation stays DB-free and golden-string testable). Link with -lpq
// (Homebrew: -I/opt/homebrew/opt/libpq/include -L/opt/homebrew/opt/libpq/lib).
//
// SEMANTICS (mirror the JVM SqlExecutor / SqlSelectRt path):
//   - Binds are 1-based positionally to $1..$N in RellSql.binds order (== render order).
//   - A SELECT decodes each row's columns by the at-expression's resolved result types; the row
//     callback receives the decoded RellValue vector.
//   - An INSERT/UPDATE/DELETE ends in RETURNING; exec_dml returns the affected row count and,
//     for snapshot/returning paths, the decoded returning rows (rowid [+ attrs]).
//   - Transactions: begin/commit/rollback issue "BEGIN"/"COMMIT"/"ROLLBACK". The interpreter
//     normally runs inside one chain transaction; the executor does NOT own connection lifetime
//     unless constructed from a conninfo string.
//
// ERROR HANDLING: a libpq failure (PQresultStatus not COMMAND_OK/TUPLES_OK) is a hard error; the
// executor surfaces it (throw rell::sql::PgError or return an error status — the integration
// chooses) so the JIT trampoline can map it to an Rt_Exception, identical to the JDBC path.
// Determinism is preserved because the SQL text + binds are byte-identical to DbSqlGen's output.

#ifndef RELL_PG_EXECUTOR_H
#define RELL_PG_EXECUTOR_H

#include <functional>
#include <stdexcept>
#include <string>
#include <vector>

#include <libpq-fe.h>

#include "rell_runtime.h"
#include "rell_sql.h"
#include "rell_sql_types.h"

namespace rell::sql {

using rell::llvm_rt::RellValue;

// A libpq-layer failure (connection or query). Carries the PG error message so the boundary can
// translate it to an Rt_Exception. (DB constraint violations etc. are reported here too.)
class PgError : public std::runtime_error {
public:
    explicit PgError(const std::string &msg) : std::runtime_error(msg) {}
};

// =====================================================================================
// PgExecutor — owns or borrows a PGconn and runs RellSql queries.
// =====================================================================================
class PgExecutor {
public:
    // Open a new connection from a libpq conninfo string ("host=... dbname=... user=...").
    // The executor owns and PQfinish-es this connection. Throws PgError if the connection is bad.
    explicit PgExecutor(const std::string &conninfo);

    // Borrow an existing connection (e.g. the one the JVM/postchain already holds, passed across
    // JNI as a pointer). The executor does NOT close it.
    explicit PgExecutor(PGconn *borrowed_conn);

    ~PgExecutor();

    PgExecutor(const PgExecutor &) = delete;
    PgExecutor &operator=(const PgExecutor &) = delete;

    // ---- queries ----------------------------------------------------------------------

    // The row sink for a SELECT: invoked once per result row with the columns decoded into
    // RellValues per `col_types` (parallel to the SELECT's what-fields). Return value ignored;
    // throw to abort. The decoded vector is reused/valid only during the call.
    using RowCallback = std::function<void(const std::vector<RellValue> &row)>;

    // Execute a SELECT (RellSql with $N params). `col_types` gives the resolved Rell type of each
    // selected column, driving decode_cell + the per-column result format. `nullable` parallels
    // col_types (whether each column may be SQL NULL — a non-nullable NULL is an error, mirroring
    // Rt_SqlNull.check). `frame_arena`/rebox context is threaded for HANDLE-producing decodes.
    // Calls `cb` for each row in result order.
    void exec_select(const RellSql &sql, const std::vector<RellSqlType> &col_types,
                     const std::vector<bool> &nullable, const RowCallback &cb);

    // Result of a DML (INSERT/UPDATE/DELETE ... RETURNING ...).
    struct DmlResult {
        int affected = 0;                              // PQcmdTuples / number of RETURNING rows.
        std::vector<std::vector<RellValue>> returning;  // decoded RETURNING rows (rowid [+attrs]).
    };

    // Execute a DML query. `returning_types`/`returning_nullable` describe the RETURNING columns
    // (e.g. {ROWID} for a plain delete, or {ROWID, attr1, attr2, ...} for a snapshot update). If
    // empty, RETURNING rows are not decoded (only the affected count is filled). Mirrors
    // executeUpdateSqlInner / executeDeleteSqlInner / buildInsertSql execution.
    DmlResult exec_dml(const RellSql &sql, const std::vector<RellSqlType> &returning_types,
                       const std::vector<bool> &returning_nullable);

    // ---- transactions -----------------------------------------------------------------
    void begin();
    void commit();
    void rollback();

    PGconn *conn() const { return conn_; }

private:
    // Build the parallel PQexecParams arrays from RellSql.binds (via bind_param), run the query,
    // and return the PGresult (caller PQclear-s). Throws PgError on a non-success status.
    // `result_format` selects text(0)/binary(1) for the whole result; per-column formats are not
    // expressible in one PQexecParams call, so the executor requests TEXT (0) results and lets
    // decode_cell parse per type — BYTEA in text format is the "\\x..." hex form, which
    // decode_cell handles. (FIDELITY: keep one result format; decode_cell branches on it.)
    PGresult *exec_params(const RellSql &sql);

    // Issue a parameterless command ("BEGIN"/"COMMIT"/"ROLLBACK"); throw PgError on failure.
    void exec_simple(const char *command);

    PGconn *conn_ = nullptr;
    bool owns_conn_ = false;
};

}  // namespace rell::sql

#endif  // RELL_PG_EXECUTOR_H
