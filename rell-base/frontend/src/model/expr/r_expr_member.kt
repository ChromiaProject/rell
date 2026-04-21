/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.model.*

class R_MemberExpr(
    val base: R_Expr,
    val calculator: R_MemberCalculator,
    val safe: Boolean,
): R_BaseExpr(C_Utils.effectiveMemberType(calculator.type, safe))

abstract class R_MemberCalculator(val type: R_Type)

class R_MemberCalculator_Error(type: R_Type, val msg: String): R_MemberCalculator(type)

class R_MemberCalculator_TupleAttr(
    type: R_Type,
    val attrIndex: Int,
): R_MemberCalculator(type)

class R_MemberCalculator_VirtualTupleAttr(
    type: R_Type,
    val fieldIndex: Int,
): R_MemberCalculator(type)

class R_MemberCalculator_VirtualStructAttr(
    type: R_Type,
    val attr: R_Attribute,
): R_MemberCalculator(type)

class R_MemberCalculator_StructAttr(
    val attr: R_Attribute,
): R_MemberCalculator(attr.type)

class R_MemberCalculator_ObjectAttr(
    val expr: R_Expr,
    resType: R_Type,
): R_MemberCalculator(resType)

class R_ObjectExpr(val objType: R_ObjectType): R_BaseExpr(objType)

class R_ObjectAttrExpr(
    type: R_Type,
    val rObject: R_ObjectDefinition,
    val atBase: Db_AtExprBase,
): R_BaseExpr(type)

class R_MemberCalculator_DataAttribute(
    type: R_Type,
    val atBase: Db_AtExprBase,
    val lambda: R_LambdaBlock,
): R_MemberCalculator(type)
