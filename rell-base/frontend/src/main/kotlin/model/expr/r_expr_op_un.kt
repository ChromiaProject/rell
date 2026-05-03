/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.R_Type

/**
 * Compile-time identity for a unary operator. No evaluate() — all evaluation logic lives
 * in [net.postchain.rell.base.runtime.evaluateUnaryOp] in runtime/rt_ops.kt.
 *
 * `kindName` is the deterministic, content-addressed key that flows into the serialized
 * RR tree. It must match the dispatch keys in `rt_ops.kt`'s `evaluateUnaryOp`. Declared
 * explicitly (rather than deriving from `javaClass.simpleName`) so the serialization
 * contract is independent of Kotlin class-name quirks — a rename here is a wire-format
 * change, and should be called out.
 */
sealed interface R_UnaryOp {
    val kindName: String
    data object Minus_Integer: R_UnaryOp { override val kindName get() = "Minus_Integer" }
    data object Minus_BigInteger: R_UnaryOp { override val kindName get() = "Minus_BigInteger" }
    data object Minus_Decimal: R_UnaryOp { override val kindName get() = "Minus_Decimal" }
    data object Not: R_UnaryOp { override val kindName get() = "Not" }
}

class R_UnaryExpr(
        type: R_Type,
        val op: R_UnaryOp,
        val expr: R_Expr,
        val errPos: ErrorPos,
): R_BaseExpr(type)
