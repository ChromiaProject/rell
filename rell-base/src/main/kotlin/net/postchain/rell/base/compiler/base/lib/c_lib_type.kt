/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.ast.S_CallArguments
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_PosValue
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.C_GlobalFunction
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.R_CtErrorType
import net.postchain.rell.base.model.R_EntityType
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_GenericType
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeParamsResolver
import net.postchain.rell.base.mtype.M_TypeSets
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.immListOf

class C_LibTypeExtension(
    private val lTypeExt: L_TypeExtension,
    private val body: C_LibTypeBody,
) {
    fun getExtStaticMembers(mType: M_Type): C_LibTypeMembers<C_TypeStaticMember>? {
        return getExtMembers0(mType, body.staticMembers, C_TypeStaticMember::replaceTypeParams)
    }

    fun getExtValueMembers(mType: M_Type): C_LibTypeMembers<C_TypeValueMember>? {
        return getExtMembers0(mType, body.valueMembers, C_TypeValueMember::replaceTypeParams)
    }

    private fun <MemberT: C_TypeMember> getExtMembers0(
        mType: M_Type,
        members: C_LibTypeMembers<MemberT>,
        replacer: (MemberT, C_TypeMemberReplacement) -> MemberT,
    ): C_LibTypeMembers<MemberT>? {
        val map = M_TypeParamsResolver.resolveTypeParams(lTypeExt.typeParams, lTypeExt.selfType, mType)
        map ?: return null

        val typeArgs = map.mapValues { M_TypeSets.one(it.value) }
        val rep = C_TypeMemberReplacement(mType, typeArgs)
        return C_LibTypeMembers.replace(members, rep, replacer)
    }
}

sealed class C_LibType(val mType: M_Type) {
    abstract fun hasConstructor(): Boolean
    abstract fun getConstructor(): C_GlobalFunction?
    abstract fun getStaticMembers(): C_LibTypeMembers<C_TypeStaticMember>
    abstract fun getValueMembers(): C_LibTypeMembers<C_TypeValueMember>

    companion object {
        fun make(
            rType: R_Type,
            doc: DocCode,
            constructorFn: C_GlobalFunction? = null,
            staticMembers: ImmList<C_TypeStaticMember> = immListOf(),
            valueMembers: Lazy<ImmList<C_TypeValueMember>> = lazyOf(immListOf()),
        ): C_LibType {
            val docCodeStrategy = L_TypeDefDocCodeStrategy { doc }
            val mType = L_TypeUtils.makeMType(rType, null, docCodeStrategy)
            return make(mType, constructorFn = constructorFn, staticMembers = staticMembers, valueMembers = valueMembers)
        }

        fun make(
            mType: M_Type,
            constructorFn: C_GlobalFunction? = null,
            staticMembers: ImmList<C_TypeStaticMember> = immListOf(),
            valueMembers: Lazy<ImmList<C_TypeValueMember>> = lazyOf(immListOf()),
        ): C_LibType {
            return C_LibType_MType(
                mType,
                constructorFn = constructorFn,
                staticMembers = staticMembers,
                valueMembers = valueMembers,
            )
        }

        fun make(
            typeDef: C_LibTypeDef,
            vararg args: R_Type,
            constructorFn: C_GlobalFunction? = null,
            valueMembers: Lazy<ImmList<C_TypeValueMember>> = lazyOf(immListOf()),
        ): C_LibType {
            checkEquals(args.size, typeDef.mGenericType.params.size) {
                "Wrong number of type arguments for '${typeDef.typeName}'"
            }
            val mArgs = args.map { it.mType }
            val lType = L_Type.make(typeDef.lTypeDef, mArgs)
            return C_LibType_TypeDef(
                typeDef,
                lType,
                extraConstructor = constructorFn,
                extraValueMembers = valueMembers,
            )
        }
    }
}

private class C_LibType_MType(
    mType: M_Type,
    private val constructorFn: C_GlobalFunction?,
    staticMembers: ImmList<C_TypeStaticMember>,
    valueMembers: Lazy<ImmList<C_TypeValueMember>>,
): C_LibType(mType) {
    private val staticMembers: C_LibTypeMembers<C_TypeStaticMember> = C_LibTypeMembers.simple(staticMembers)

    private val valueMembersLazy: C_LibTypeMembers<C_TypeValueMember> by lazy {
        C_LibTypeMembers.simple(valueMembers.value)
    }

    override fun hasConstructor() = constructorFn != null
    override fun getConstructor() = constructorFn
    override fun getStaticMembers() = staticMembers
    override fun getValueMembers() = valueMembersLazy
}

private class C_LibType_TypeDef(
    private val typeDef: C_LibTypeDef,
    private val lType: L_Type,
    private val extraConstructor: C_GlobalFunction?,
    private val extraValueMembers: Lazy<ImmList<C_TypeValueMember>>,
): C_LibType(lType.mType) {
    private val constructorLazy: C_GlobalFunction? by lazy {
        C_LibTypeAdapter.makeConstructor(lType, typeDef.constructors)
    }

    private val valueMembersLazy: C_LibTypeMembers<C_TypeValueMember> by lazy {
        val extraMems = extraValueMembers.value
        if (extraMems.isEmpty()) typeDef.valueMembers else {
            val extraValueMembersTbl = C_LibTypeMembers.simple(extraMems)
            C_LibTypeMembers.combined(listOf(extraValueMembersTbl, typeDef.valueMembers))
        }
    }

    override fun hasConstructor() = extraConstructor != null || constructorLazy != null
    override fun getConstructor() = extraConstructor ?: constructorLazy
    override fun getStaticMembers() = typeDef.staticMembers
    override fun getValueMembers() = valueMembersLazy
}

sealed class C_TypeDef {
    abstract fun hasConstructor(): Boolean
    open fun isEntity(): Boolean = false
    abstract fun compileExpr(msgCtx: C_MessageContext, pos: S_Pos): C_Expr
    abstract fun compileType(ctx: C_AppContext, pos: S_Pos, args: List<S_PosValue<R_Type>>): R_Type

    companion object {
        fun makeRType(rType: R_Type): C_TypeDef {
            return C_RTypeDef(rType)
        }
    }
}

private class C_RTypeDef(
    private val rType: R_Type,
): C_TypeDef() {
    override fun hasConstructor() = false
    override fun isEntity() = rType is R_EntityType
    override fun compileExpr(msgCtx: C_MessageContext, pos: S_Pos): C_Expr = C_SpecificTypeExpr(pos, rType)

    override fun compileType(ctx: C_AppContext, pos: S_Pos, args: List<S_PosValue<R_Type>>): R_Type {
        return if (args.isEmpty()) rType else {
            ctx.msgCtx.error(pos, "type:not_generic:${rType.strCode()}", "Type '${rType.str()}' is not generic")
            return R_CtErrorType
        }
    }
}

class C_LibTypeItem<T>(
    val simpleName: R_Name,
    val docSymbol: DocSymbol,
    val restrictions: C_MemberRestrictions,
    val member: T,
)

class C_LibTypeConstructors(
    val constructors: ImmList<C_LibTypeItem<L_Constructor>>,
    val specialConstructors: ImmList<C_LibTypeItem<C_SpecialLibGlobalFunctionBody>>,
)

class C_LibTypeBody(
    val constructors: C_LibTypeConstructors,
    val rawConstructor: C_GlobalFunction?,
    val staticMembers: C_LibTypeMembers<C_TypeStaticMember>,
    val valueMembers: C_LibTypeMembers<C_TypeValueMember>,
)

class C_LibTypeDef(
    val typeName: String,
    val lTypeDef: L_TypeDef,
    body: C_LibTypeBody,
): C_TypeDef() {
    val mGenericType: M_GenericType = lTypeDef.mGenericType

    private val mType0: M_Type? = if (mGenericType.params.isEmpty()) mGenericType.getType() else null

    val mType: M_Type
        get() {
            return checkNotNull(mType0) { "Not a simple type: ${mGenericType.strCode()}" }
        }

    val constructors = body.constructors
    val rawConstructor = body.rawConstructor
    val staticMembers = body.staticMembers
    val valueMembers = body.valueMembers

    override fun hasConstructor() = rawConstructor != null

    override fun compileExpr(msgCtx: C_MessageContext, pos: S_Pos): C_Expr {
        return compileExprLibType(msgCtx, pos, C_UniqueDefaultIdeInfoPtr())
    }

    fun compileExprLibType(msgCtx: C_MessageContext, pos: S_Pos, ideInfoPtr: C_UniqueDefaultIdeInfoPtr): C_Expr {
        return if (mGenericType.params.isEmpty()) {
            val rType = getRType(msgCtx, pos, immListOf())
            C_SpecificTypeExpr(pos, rType, ideInfoPtr)
        } else {
            C_RawGenericTypeExpr(pos, this, ideInfoPtr)
        }
    }

    override fun compileType(ctx: C_AppContext, pos: S_Pos, args: List<S_PosValue<R_Type>>): R_Type {
        if (!checkArgCount(ctx.msgCtx, pos, args.size)) {
            return R_CtErrorType
        }

        val checkedArgs = checkTypeArgs(ctx, args)
        return getRType(ctx.msgCtx, pos, checkedArgs)
    }

    private fun checkArgCount(msgCtx: C_MessageContext, pos: S_Pos, argCount: Int): Boolean {
        val paramCount = mGenericType.params.size
        if (paramCount == 0) {
            if (argCount != 0) {
                msgCtx.error(pos, "type:not_generic:$typeName", "Type '$typeName' is not generic")
                return false
            }
        } else if (argCount == 0) {
            msgCtx.error(pos, "type:generic:no_args:$typeName",
                "Type arguments not specified for generic type '$typeName'")
            return false
        } else if (argCount != paramCount) {
            msgCtx.error(pos, "type:generic:wrong_arg_count:$typeName:$paramCount:$argCount",
                "Wrong number of type arguments for type '$typeName': $argCount instead of $paramCount")
            return false
        }
        return true
    }

    private fun checkTypeArgs(ctx: C_AppContext, args: List<S_PosValue<R_Type>>): List<R_Type> {
        val checkedArgs = args.map { arg ->
            C_Types.checkNotUnit(ctx.msgCtx, arg.pos, arg.value, null) {
                val typeName = lTypeDef.fullName.qualifiedName
                "$typeName:component" toCodeMsg "$typeName component"
            }
        }

        val toValidate = checkedArgs.withIndex().filter { (i, rType) ->
            mGenericType.params[i].bounds != M_TypeSets.ALL && rType.isNotError()
        }

        if (toValidate.isNotEmpty()) {
            ctx.executor.onPass(C_CompilerPass.VALIDATION) {
                validateTypeArgs(ctx.msgCtx, args, toValidate)
            }
        }

        return checkedArgs
    }

    private fun getRType(msgCtx: C_MessageContext, pos: S_Pos, args: List<R_Type>): R_Type {
        val rType = lTypeDef.rTypeFactory?.getRType(args)
        return if (rType != null) rType else {
            msgCtx.error(pos, "generic_type:no_rtype_factory:${mGenericType.name}",
                "Cannot use type ${mGenericType.name}")
            R_CtErrorType
        }
    }

    private fun validateTypeArgs(
        msgCtx: C_MessageContext,
        args: List<S_PosValue<R_Type>>,
        toValidate: List<IndexedValue<R_Type>>,
    ) {
        val genType = lTypeDef.mGenericType

        for ((i, rType) in toValidate) {
            val param = genType.params[i]
            val mTypeArg = rType.mType.oneTypeSet
            val codeMsg = M_GenericType.checkParamBounds(genType, param, mTypeArg)
            if (codeMsg != null) {
                val argPos = args[i].pos
                msgCtx.error(argPos, codeMsg.first, codeMsg.second)
            }
        }
    }
}

private class C_RawGenericTypeExpr(
    private val pos: S_Pos,
    private val typeDef: C_LibTypeDef,
    private val ideInfoPtr: C_UniqueDefaultIdeInfoPtr,
): C_NoValueExpr() {
    override fun startPos() = pos

    override fun member(ctx: C_ExprContext, memberNameHand: C_NameHandle, exprHint: C_ExprHint): C_Expr {
        ideInfoPtr.setDefault()
        val memberName = memberNameHand.name
        val nameCode = "[${typeDef.lTypeDef.qualifiedName}]:$memberName"
        val nameMsg = "${typeDef.typeName}.$memberName"
        C_Errors.errUnknownName(ctx.msgCtx, pos, nameCode, nameMsg)
        memberNameHand.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
        return C_ExprUtils.errorExpr(ctx, memberName.pos)
    }

    override fun isCallable(): Boolean {
        return typeDef.rawConstructor != null
    }

    override fun call(ctx: C_ExprContext, pos: S_Pos, args: S_CallArguments, resTypeHint: C_TypeHint): C_Expr {
        val fn = typeDef.rawConstructor
        if (fn == null) {
            ideInfoPtr.setDefault()
            return super.call(ctx, pos, args, resTypeHint)
        }

        val lazyName = LazyPosString.of(this.pos) { typeDef.typeName }
        val vCall = fn.compileCall(ctx, lazyName, args, resTypeHint)
        ideInfoPtr.setIdeInfoOrDefault(vCall.ideInfo)

        val vExpr = vCall.vExpr()
        return C_ValueExpr(vExpr)
    }

    override fun errKindName() = "type" to typeDef.lTypeDef.qualifiedName.str()
}
