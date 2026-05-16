/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.rr.RR_Type

fun Ld_FunctionMetaBodyDsl.typeArgR(name: String): R_Type = fnBodyMeta.typeArg(name)

fun Ld_FunctionMetaBodyDsl.typeArgsR(name1: String, name2: String): Pair<R_Type, R_Type> =
    fnBodyMeta.typeArgs(name1, name2)

val Ld_FunctionMetaBodyDsl.selfTypeR: R_Type
    get() = fnBodyMeta.rSelfType

val Ld_FunctionMetaBodyDsl.resultTypeR: R_Type
    get() = fnBodyMeta.rResultType

/**
 * RR_Type accessors usable for capabilities that depend only on type structure (e.g.
 * comparator). For full runtime type-class resolution prefer [typeArgR] + interpreter.resolveRType.
 *
 * RR types built here are stubs (def-backed types use index `-1`) and are suitable for
 * structural inspection (kind, virtual-ness, comparator). They must NOT be passed to
 * [Rt_Interpreter.resolveType] — that path requires the resolved indices and will fail or
 * produce a stub class. Use [typeArgR] + `interpreter.resolveRType` when an [Rt_ValueClass]
 * is needed.
 */
fun Ld_FunctionMetaBodyDsl.typeArgRrType(name: String): RR_Type =
    rTypeToRRType(fnBodyMeta.typeArg(name))

fun Ld_FunctionMetaBodyDsl.typeArgsRrType(name1: String, name2: String): Pair<RR_Type, RR_Type> {
    val (t1, t2) = fnBodyMeta.typeArgs(name1, name2)
    return rTypeToRRType(t1) to rTypeToRRType(t2)
}

val Ld_FunctionMetaBodyDsl.selfTypeRr: RR_Type
    get() = rTypeToRRType(fnBodyMeta.rSelfType)

val Ld_FunctionMetaBodyDsl.resultTypeRr: RR_Type
    get() = rTypeToRRType(fnBodyMeta.rResultType)
