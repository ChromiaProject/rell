/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

/**
 * Status codes returned from [net.postchain.rell.base.runtime.truffle.nodes.Tf_ExprNode.executeStmt].
 *
 * The Truffle backend used to encode `return` / `break` / `continue` as JVM exceptions
 * (`Tf_ReturnException` / `Tf_BreakException` / `Tf_ContinueException`). On FT4-style
 * workloads with many small Rell function calls and stdlib boundaries that block PE-inlining,
 * those exceptions are not folded by Graal — `ExceptionHandlerStub` / `UnwindException` /
 * `JVMCIRuntime::exception_handler_for_pc` dominated the profile (>60% of CPU time on
 * `rule_eval`).
 *
 * Replacement: every statement node returns one of these integer codes from `executeStmt`.
 * Loop and block nodes propagate or consume the code locally; the function-body root reads
 * the return value from the dedicated [TF_RETURN_VALUE_AUX_SLOT] aux slot when status is
 * [STATUS_RETURN]. No exception is thrown; PE compiles the status check to a primitive
 * integer comparison plus a [com.oracle.truffle.api.profiles.BranchProfile] for the cold
 * non-fallthrough branches.
 */
internal const val STATUS_FALLTHROUGH: Int = 0

/** Status: `return` — the return value (or null for `return;`) is in [TF_RETURN_VALUE_AUX_SLOT]. */
internal const val STATUS_RETURN: Int = 1

/** Status: `break` — caught by the nearest enclosing loop. */
internal const val STATUS_BREAK: Int = 2

/** Status: `continue` — caught by the nearest enclosing loop. */
internal const val STATUS_CONTINUE: Int = 3
