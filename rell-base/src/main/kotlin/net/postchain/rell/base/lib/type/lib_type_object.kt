/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.core.C_QualifiedName
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.lib.V_SpecialMemberFunctionCall
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ExprInfo
import net.postchain.rell.base.compiler.vexpr.V_GlobalConstantRestriction
import net.postchain.rell.base.compiler.vexpr.V_TypeValueMember
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_ObjectValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf

object Lib_Type_Object {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("object", abstract = true, hidden = true) {
            supertypeStrategySpecial { mType ->
                val rType = L_TypeUtils.getRType(mType)
                rType is R_ObjectType
            }
        }

        namespace("rell") {
            extension("object_ext", type = "object") {
                function("to_struct", C_Fn_ToStruct(false)) {
                    comment("""
                        Convert this instance to a `struct<T>`.
                        Note that this will read all values from the database.
                    """)
                }
                function("to_mutable_struct", C_Fn_ToStruct(true)) {
                    comment("""
                        Convert this instance to a `mutable struct<T>`.
                        Note that this will read all values from the database.
                    """)
                }
            }
        }
    }

    fun getMemberValues(type: R_ObjectType): List<C_TypeValueMember> {
        val rObject = type.rObject
        return rObject.rEntity.attributes.values.map { C_TypeValueMember_ObjectAttr(rObject, it) }
    }

    private class C_TypeValueMember_ObjectAttr(
        private val rObject: R_ObjectDefinition,
        private val attr: R_Attribute,
    ): C_TypeValueMember_Value(attr.ideName, attr.type) {
        override fun kindMsg() = "attribute"
        override fun nameMsg(): C_CodeMsg = attr.rName.str toCodeMsg attr.rName.str

        override fun value(ctx: C_ExprContext, linkPos: S_Pos, linkName: C_Name?): V_TypeValueMember {
            return V_TypeValueMember_ObjectAttr(ctx, linkPos, linkName, rObject, attr)
        }
    }

    private class V_TypeValueMember_ObjectAttr(
        private val exprCtx: C_ExprContext,
        private val memberPos: S_Pos,
        private val memberName: C_Name?,
        private val rObject: R_ObjectDefinition,
        private val attr: R_Attribute,
    ): V_TypeValueMember(attr.type, attr.ideInfo) {
        override fun implicitAttrName() = memberName
        override fun vExprs() = immListOf<V_Expr>()
        override fun globalConstantRestriction() = V_GlobalConstantRestriction("object_attr", null)

        override fun calculator(): R_MemberCalculator {
            val rEntity = rObject.rEntity
            val atEntity = exprCtx.makeAtEntity(rEntity, exprCtx.appCtx.nextAtExprId())
            val whatExpr = Db_AttrExpr(Db_EntityExpr(atEntity), attr)
            val whatValue = Db_AtWhatValue_DbExpr(whatExpr, whatExpr.type)
            val whatField = Db_AtWhatField(R_AtWhatFieldFlags.DEFAULT, whatValue)
            val rExpr: R_Expr = ObjectUtils.createObjectMemberRExpr(rObject, atEntity, whatField, attr.type)
            return R_MemberCalculator_ObjectAttr(rExpr, attr.type)
        }

        override fun destination(base: V_Expr): C_Destination {
            if (!attr.mutable) {
                val fullName = "${rObject.defName.qualifiedName}.${attr.name}"
                throw C_Errors.errAttrNotMutable(memberPos, attr.name, fullName)
            }
            exprCtx.checkDbUpdateAllowed(memberPos)
            return C_Destination_ObjectAttr(rObject, attr)
        }
    }

    private class R_MemberCalculator_ObjectAttr(private val expr: R_Expr, resType: R_Type): R_MemberCalculator(resType) {
        override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
            return expr.evaluate(frame)
        }
    }

    private class C_Fn_ToStruct(
        private val mutable: Boolean
    ): Lib_Type_Entity.C_SysFn_ToStruct_Common() {
        override fun compile0(ctx: C_ExprContext, selfType: R_Type): V_SpecialMemberFunctionCall? {
            val objectType = selfType as? R_ObjectType
            objectType ?: return null
            val struct = objectType.rObject.rEntity.mirrorStructs.getStruct(mutable)
            return V_SpecialMemberFunctionCall_ObjectToStruct(ctx, objectType, struct)
        }
    }
}

class V_ObjectExpr(
    exprCtx: C_ExprContext,
    qName: C_QualifiedName,
    private val rObject: R_ObjectDefinition
): V_Expr(exprCtx, qName.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(rObject.type)
    override fun globalConstantRestriction() = V_GlobalConstantRestriction("object", null)
    override fun toRExpr0(): R_Expr = R_ObjectExpr(rObject.type)

    override fun getDefMeta(): R_DefinitionMeta {
        return R_DefinitionMeta(rObject.defName, mountName = rObject.rEntity.mountName)
    }
}

private class R_ObjectExpr(private val objType: R_ObjectType): R_Expr(objType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return Rt_ObjectValue(objType)
    }
}

private class R_ObjectAttrExpr(
    type: R_Type,
    private val rObject: R_ObjectDefinition,
    private val atBase: Db_AtExprBase,
): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        var records = atBase.execute(frame, Rt_AtExprExtras.NULL)

        if (records.isEmpty()) {
            val forced = frame.defCtx.appCtx.forceObjectInit(rObject)
            if (forced) {
                records = atBase.execute(frame, Rt_AtExprExtras.NULL)
            }
        }

        val count = records.size

        if (count == 0) {
            val name = rObject.appLevelName
            throw Rt_Exception.common("obj_norec:$name", "No record for object '$name' in database")
        } else if (count > 1) {
            val name = rObject.appLevelName
            throw Rt_Exception.common("obj_multirec:$name:$count", "Multiple records for object '$name' in database: $count")
        }

        val record = records[0]
        checkEquals(record.size, 1)
        val value = record[0]
        return value
    }
}

private class V_SpecialMemberFunctionCall_ObjectToStruct(
    exprCtx: C_ExprContext,
    private val objectType: R_ObjectType,
    private val struct: R_Struct,
): V_SpecialMemberFunctionCall(exprCtx, struct.type) {
    private val structType = struct.type

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("object_to_struct", null)

    override fun calculator(): R_MemberCalculator {
        val atEntity = exprCtx.makeAtEntity(objectType.rObject.rEntity, exprCtx.appCtx.nextAtExprId())
        val whatValue = createWhatValue(Db_EntityExpr(atEntity))
        val whatField = Db_AtWhatField(R_AtWhatFieldFlags.DEFAULT, whatValue)
        val rExpr = ObjectUtils.createObjectMemberRExpr(objectType.rObject, atEntity, whatField, structType)
        return R_MemberCalculator_ObjectAttr(rExpr, structType)
    }

    private fun createWhatValue(dbEntityExpr: Db_TableExpr): Db_AtWhatValue {
        val rEntity = objectType.rObject.rEntity
        val dbExprs = rEntity.attributes.values.map {
            C_EntityAttrRef.create(rEntity, it).createDbContextAttrExpr(dbEntityExpr)
        }
        return Db_AtWhatValue_ToStruct(struct, dbExprs)
    }

    override fun canBeDbExpr() = true

    private class R_MemberCalculator_ObjectAttr(private val expr: R_Expr, resType: R_Type): R_MemberCalculator(resType) {
        override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
            return expr.evaluate(frame)
        }
    }
}

private object ObjectUtils {
    fun createObjectMemberRExpr(
        rObject: R_ObjectDefinition,
        atEntity: R_DbAtEntity,
        whatField: Db_AtWhatField,
        resType: R_Type
    ): R_Expr {
        val from = listOf(atEntity)
        val what = listOf(whatField)
        val atBase = Db_AtExprBase(from, what, null, isMany = false)
        return R_ObjectAttrExpr(resType, rObject, atBase)
    }
}
