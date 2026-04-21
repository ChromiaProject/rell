/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals

abstract class R_FunctionCall(val returnType: R_Type)

class R_FullFunctionCall(
        returnType: R_Type,
        val target: R_FunctionCallTarget,
        val callPos: FilePos,
        val args: ImmList<R_Expr>,
        val mapping: ImmList<Int>,
): R_FunctionCall(returnType) {
    init {
        checkEquals(this.mapping.sorted(), this.args.indices.toList())
    }
}

class R_PartialArgMapping(val wild: Boolean, val index: Int)

class R_PartialCallMapping(val exprCount: Int, val wildCount: Int, val args: ImmList<R_PartialArgMapping>) {
    init {
        check(exprCount >= 0)
        check(wildCount >= 0)
        checkEquals(this.args.size, exprCount + wildCount)
        checkEquals(this.args.filter { it.wild }.map { it.index }.sorted().toList(), (0 until wildCount).toList())
        checkEquals(this.args.filter { !it.wild }.map { it.index }.sorted().toList(), (0 until exprCount).toList())
    }
}

class R_PartialFunctionCall(
    returnType: R_Type,
    val target: R_FunctionCallTarget,
    val mapping: R_PartialCallMapping,
    val args: ImmList<R_Expr>,
): R_FunctionCall(returnType) {
    init {
        checkEquals(this.args.size, mapping.exprCount)
    }
}

class R_FunctionCallExpr(
    type: R_Type,
    val base: R_Expr?,
    val call: R_FunctionCall,
    val safe: Boolean,
): R_BaseExpr(type)
