/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl

fun Ld_FunctionMetaBodyDsl.typeArgRt(name: String): Rt_ValueClass<*> = rTypeToRtType(fnBodyMeta.typeArg(name))

fun Ld_FunctionMetaBodyDsl.typeArgsRt(name1: String, name2: String): Pair<Rt_ValueClass<*>, Rt_ValueClass<*>> {
    val (a, b) = fnBodyMeta.typeArgs(name1, name2)
    return rTypeToRtType(a) to rTypeToRtType(b)
}

val Ld_FunctionMetaBodyDsl.selfTypeRt: Rt_ValueClass<*>
    get() = rTypeToRtType(fnBodyMeta.rSelfType)

val Ld_FunctionMetaBodyDsl.resultTypeRt: Rt_ValueClass<*>
    get() = rTypeToRtType(fnBodyMeta.rResultType)
