/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.fn.C_FullCallArguments
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallArgsUtils
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTargetInfo
import net.postchain.rell.base.compiler.base.fn.C_PartialCallArguments
import net.postchain.rell.base.compiler.base.lib.*
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_MemberFunctionCall
import net.postchain.rell.base.compiler.vexpr.V_TypeValueMember
import net.postchain.rell.base.lib.type.R_RowidType
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.doc.DocDeclaration_EntityAttribute
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.doc.DocSymbolName
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList

class C_MemberLink(
    val base: V_Expr,
    val selfType: R_Type,
    val linkPos: S_Pos,
    val linkName: C_Name?,
    val safe: Boolean,
)

abstract class C_TypeValueMember(optionalName: R_Name?): C_TypeMember(optionalName) {
    abstract fun nameMsg(): C_CodeMsg
    abstract override fun replaceTypeParams(rep: C_TypeMemberReplacement): C_TypeValueMember

    abstract fun value(ctx: C_ExprContext, linkPos: S_Pos, linkName: C_Name?): V_TypeValueMember

    open fun call(
        ctx: C_ExprContext,
        selfType: R_Type,
        linkPos: S_Pos,
        args: List<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_TypeValueMember? = null

    abstract fun compile(ctx: C_ExprContext, link: C_MemberLink, ideInfoHand: C_IdeSymbolInfoHandle): C_Expr
}

abstract class C_TypeValueMember_Value(
    private val ideName: R_IdeName?,
    val valueType: R_Type,
    private val restrictions: C_MemberRestrictions,
): C_TypeValueMember(ideName?.rName) {
    final override fun isValue() = true
    final override fun isCallable() = valueType is R_FunctionType

    // Not supported yet.
    final override fun replaceTypeParams(rep: C_TypeMemberReplacement) = this

    final override fun compile(ctx: C_ExprContext, link: C_MemberLink, ideInfoHand: C_IdeSymbolInfoHandle): C_Expr {
        val expr: C_Expr = C_ValueMemberExpr(ctx, link, this, C_IdeSymbolInfoHandle.NOP_HANDLE)
        val ideInfo = ideName?.ideInfo ?: C_IdeSymbolInfo.UNKNOWN
        ideInfoHand.setIdeInfo(ideInfo)
        restrictions.access(ctx.msgCtx, link.linkPos)
        return expr
    }
}

class C_TypeValueMember_BasicAttr(
    private val attr: C_MemberAttr,
): C_TypeValueMember_Value(attr.ideName, attr.type, attr.restrictions()) {
    override fun kindMsg() = "attribute"
    override fun nameMsg() = attr.nameMsg()

    override fun value(ctx: C_ExprContext, linkPos: S_Pos, linkName: C_Name?): V_TypeValueMember {
        val vAttr = attr.vAttr(ctx, linkPos)
        val ideInfo = attr.ideName?.ideInfo ?: C_IdeSymbolInfo.UNKNOWN
        return V_TypeValueMember_BasicAttr(vAttr, linkPos, linkName, ideInfo)
    }

    private class V_TypeValueMember_BasicAttr(
        private val attr: V_MemberAttr,
        private val memberPos: S_Pos,
        private val memberName: C_Name?,
        ideInfo: C_IdeSymbolInfo,
    ): V_TypeValueMember(attr.type, ideInfo) {
        override fun implicitAttrName() = memberName
        override fun vExprs() = immListOf<V_Expr>()
        override fun calculator() = attr.calculator()

        override fun destination(base: V_Expr): C_Destination {
            val rBase = base.toRExpr()
            val rDstExpr = attr.destination(memberPos, rBase)
            return C_Destination_Simple(rDstExpr)
        }

        override fun canBeDbExpr(safe: Boolean) = !safe && attr.canBeDbExpr()
        override fun dbExpr(base: Db_Expr) = attr.dbExpr(base)
    }
}

class C_TypeValueMember_Function(
    private val rName: R_Name,
    private val fn: C_LibMemberFunction,
    private val naming: C_MemberNaming,
): C_TypeValueMember(rName) {
    override fun kindMsg() = "function"
    override fun nameMsg() = rName.str toCodeMsg rName.str
    override fun isValue() = false
    override fun isCallable() = true

    override fun replaceTypeParams(rep: C_TypeMemberReplacement): C_TypeValueMember {
        val fn2 = fn.replaceTypeParams(rep)
        return if (fn2 === fn) this else C_TypeValueMember_Function(rName, fn2, naming)
    }

    override fun value(ctx: C_ExprContext, linkPos: S_Pos, linkName: C_Name?): V_TypeValueMember {
        val codeMsg = C_NoValueExpr.errNoValue(kindMsg(), nameMsg().msg)
        ctx.msgCtx.error(linkPos, codeMsg)
        return C_ExprUtils.errorVMember(linkPos, ideInfo = fn.getDefaultIdeInfo())
    }

    override fun call(
        ctx: C_ExprContext,
        selfType: R_Type,
        linkPos: S_Pos,
        args: List<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_TypeValueMember {
        val callTargetInfo = C_FunctionCallTargetInfo_LibMemberFunction(selfType, fn)
        val callArgs = C_FunctionCallArgsUtils.compileCallArgs(ctx, args, callTargetInfo)
        val fullName = naming.replaceSelfType(selfType.mType).fullNameLazy

        val callCtx = C_LibFuncCaseCtx(linkPos, fullName)

        val vCall = when (callArgs) {
            null -> null
            is C_FullCallArguments -> {
                fn.compileCallFull(ctx, callCtx, selfType, callArgs, resTypeHint)
            }
            is C_PartialCallArguments -> {
                val fnType = resTypeHint.getFunctionType()
                fn.compileCallPartial(ctx, callCtx, selfType, callArgs, fnType)
            }
        }

        callArgs?.setArgIdeInfos(vCall?.argIdeInfos ?: fn.getDefaultArgIdeInfos())

        vCall ?: return C_ExprUtils.errorVMember(linkPos, ideInfo = fn.getDefaultIdeInfo())

        val retType = vCall.returnType()
        return V_TypeValueMember_FunctionCall(retType, vCall, linkPos)
    }

    override fun compile(ctx: C_ExprContext, link: C_MemberLink, ideInfoHand: C_IdeSymbolInfoHandle): C_Expr {
        return C_ValueMemberExpr(ctx, link, this, ideInfoHand)
    }

    private class V_TypeValueMember_FunctionCall(
        type: R_Type,
        private val call: V_MemberFunctionCall,
        private val memberPos: S_Pos,
    ): V_TypeValueMember(type, call.ideInfo) {
        override fun implicitAttrName() = null
        override fun postVarFacts() = call.postVarFacts()
        override fun vExprs() = call.vExprs()
        override fun globalConstantRestriction() = call.globalConstantRestriction()
        override fun safeCallable() = false

        override fun calculator() = call.calculator()
        override fun destination(base: V_Expr) = throw C_Errors.errBadDestination(memberPos)

        override fun canBeDbExpr(safe: Boolean) = !safe && call.canBeDbExpr()
        override fun dbExpr(base: Db_Expr) = call.dbExpr(base)
        override fun dbExprWhat(base: V_Expr, safe: Boolean) = call.dbExprWhat(base, safe)
    }

    private class C_FunctionCallTargetInfo_LibMemberFunction(
        val selfType: R_Type,
        val fn: C_LibMemberFunction,
    ): C_FunctionCallTargetInfo() {
        override fun retType() = null
        override fun typeHints() = fn.getCallTypeHints(selfType)
    }
}

abstract class C_MemberAttr(
    val ideName: R_IdeName?,
    val type: R_Type,
) {
    abstract fun nameMsg(): C_CodeMsg
    abstract fun restrictions(): C_MemberRestrictions
    abstract fun vAttr(exprCtx: C_ExprContext, pos: S_Pos): V_MemberAttr
}

abstract class V_MemberAttr(val type: R_Type) {
    abstract fun calculator(): R_MemberCalculator
    abstract fun destination(pos: S_Pos, base: R_Expr): R_DestinationExpr
    open fun canBeDbExpr(): Boolean = false
    open fun dbExpr(base: Db_Expr): Db_Expr? = null
}

abstract class C_MemberAttr_TupleAttr(
    type: R_Type,
    protected val fieldIndex: Int,
    protected val field: R_TupleField,
): C_MemberAttr(field.name, type) {
    final override fun nameMsg(): C_CodeMsg {
        return if (field.name != null) {
            field.name.str toCodeMsg field.name.str
        } else {
            "$fieldIndex" toCodeMsg "[$fieldIndex]"
        }
    }

    final override fun restrictions() = C_MemberRestrictions.NULL

    protected abstract class V_MemberAttr_TupleAttr(
        type: R_Type,
        protected val fieldIndex: Int,
    ): V_MemberAttr(type) {
        final override fun destination(pos: S_Pos, base: R_Expr) = throw C_Errors.errBadDestination(pos)
    }
}

abstract class C_MemberAttr_StructAttr(type: R_Type, protected val attr: R_Attribute): C_MemberAttr(attr.ideName, type) {
    final override fun nameMsg() = attr.rName.str toCodeMsg attr.rName.str
    final override fun restrictions() = attr.restrictions

    protected abstract class V_MemberAttr_StructAttr(type: R_Type, protected val attr: R_Attribute): V_MemberAttr(type)
}

class C_MemberAttr_SysProperty(
    ideName: R_IdeName,
    type: R_Type,
    private val fn: C_SysFunction,
    private val naming: C_MemberNaming,
    private val restrictions: C_MemberRestrictions,
): C_MemberAttr(ideName, type) {
    private val name = ideName.rName

    override fun nameMsg() = name.str toCodeMsg name.str
    override fun restrictions() = restrictions

    override fun vAttr(exprCtx: C_ExprContext, pos: S_Pos): V_MemberAttr {
        val cBody = fn.compileCall(C_SysFunctionCtx(exprCtx, pos))
        return V_MemberAttr_SysProperty(type, name, naming, cBody)
    }

    private class V_MemberAttr_SysProperty(
        type: R_Type,
        private val name: R_Name,
        private val naming: C_MemberNaming,
        private val cBody: C_SysFunctionBody,
    ): V_MemberAttr(type) {
        override fun calculator(): R_MemberCalculator {
            return R_MemberCalculator_SysProperty(type, cBody)
        }

        override fun destination(pos: S_Pos, base: R_Expr): R_DestinationExpr {
            val fullName = naming.replaceSelfType(base.type.mType).fullNameLazy.value
            throw C_Errors.errAttrNotMutable(pos, name.str, fullName)
        }

        override fun canBeDbExpr() = cBody.dbFn != null

        override fun dbExpr(base: Db_Expr): Db_Expr? {
            val dbFn = cBody.dbFn
            dbFn ?: return null
            return Db_CallExpr(type, dbFn, immListOf(base))
        }
    }

    private class R_MemberCalculator_SysProperty(
        type: R_Type,
        private val cBody: C_SysFunctionBody,
    ): R_MemberCalculator(type) {
        override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
            val args = immListOf(baseValue)
            val callCtx = frame.callCtx()
            return cBody.rFn.call(callCtx, args)
        }
    }
}

sealed class C_EntityAttrRef(
    val rEntity: R_EntityDefinition,
    val ideName: R_IdeName,
    val type: R_Type,
) {
    val attrName = ideName.rName

    abstract fun attribute(): R_Attribute?
    abstract fun createDbContextAttrExpr(baseExpr: Db_TableExpr): Db_Expr
    abstract fun createDbMemberExpr(ctx: C_ExprContext, base: Db_TableExpr): Db_Expr

    companion object {
        private const val ROWID_NAME = "rowid"
        val ROWID_RNAME = R_Name.of(ROWID_NAME)
        val ROWID_TYPE: R_Type = R_RowidType

        fun isAllowedRegularAttrName(name: String) = name != ROWID_NAME

        fun create(rEntity: R_EntityDefinition, attr: R_Attribute): C_EntityAttrRef {
            return C_EntityAttrRef_Regular(rEntity, attr)
        }

        fun getEntityAttrs(rEntity: R_EntityDefinition): List<C_EntityAttrRef> {
            val docSymbol = makeRowidDocSymbol(rEntity)
            val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.MEM_ENTITY_ATTR_ROWID, doc = docSymbol)
            val rowid = C_EntityAttrRef_Rowid(rEntity, ideInfo)

            val attrs = rEntity.attributes.values.map { C_EntityAttrRef_Regular(rEntity, it) }
            return (immListOf(rowid) + attrs).toImmList()
        }

        private fun makeRowidDocSymbol(rEntity: R_EntityDefinition): DocSymbol {
            val docName = DocSymbolName.global(rEntity.defName.module, "${rEntity.defName.qualifiedName}.${ROWID_NAME}")

            val docDec = DocDeclaration_EntityAttribute(
                simpleName = ROWID_RNAME,
                type = L_TypeUtils.docType(R_RowidType.mType),
                isMutable = false,
                keyIndexKind = R_KeyIndexKind.KEY,
            )

            return DocSymbol(DocSymbolKind.ENTITY_ATTR, docName, null, docDec, null)
        }
    }
}

private class C_EntityAttrRef_Regular(
    rEntity: R_EntityDefinition,
    private val attr: R_Attribute,
): C_EntityAttrRef(rEntity, attr.ideName, attr.type) {
    override fun attribute() = attr

    override fun createDbContextAttrExpr(baseExpr: Db_TableExpr): Db_Expr {
        return makeDbAttrExpr(baseExpr)
    }

    override fun createDbMemberExpr(ctx: C_ExprContext, base: Db_TableExpr): Db_Expr {
        return makeDbAttrExpr(base)
    }

    private fun makeDbAttrExpr(base: Db_TableExpr): Db_Expr {
        val attrType = attr.type
        val resultEntity = (attrType as? R_EntityType)?.rEntity
        val resultType = if (base.type is R_NullableType) C_Types.toNullable(attrType) else attrType
        return if (resultEntity == null) {
            Db_AttrExpr(base, attr, resultType)
        } else {
            Db_RelExpr(base, attr, resultEntity, resultType)
        }
    }
}

private class C_EntityAttrRef_Rowid(
    rEntity: R_EntityDefinition,
    ideInfo: C_IdeSymbolInfo,
): C_EntityAttrRef(rEntity, R_IdeName(ROWID_RNAME, ideInfo), ROWID_TYPE) {
    override fun attribute() = null

    override fun createDbContextAttrExpr(baseExpr: Db_TableExpr): Db_Expr {
        return Db_RowidExpr(baseExpr)
    }

    override fun createDbMemberExpr(ctx: C_ExprContext, base: Db_TableExpr): Db_Expr {
        return Db_RowidExpr(base)
    }
}
