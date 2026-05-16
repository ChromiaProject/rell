/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.lmodel.L_TypeUtils.docFunctionParam
import net.postchain.rell.base.model.DefinitionName
import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.mtype.M_FunctionParam
import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.mtype.M_Type_Nullable
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_PrimitiveFactory
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*

abstract class Ld_CommonFunctionDslImpl(
    private val commonMaker: Ld_CommonFunctionMaker,
    private val bodyDsl: Ld_FunctionBodyDsl,
    private val receiverSlots: Int,
): Ld_CommonFunctionDsl, Ld_FunctionBodyDsl by bodyDsl, Ld_MemberDsl by Ld_MemberDslImpl(commonMaker) {
    override val fnSimpleName: String
        get() = bodyDsl.fnSimpleName

    override fun deprecated(newName: String, error: Boolean) {
        commonMaker.deprecated(C_Deprecated(useInstead = newName, error = error))
    }

    override fun generic(
        name: String,
        subOf: String?,
        superOf: String?,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        commonMaker.generic(name = name, subOf = subOf, superOf = superOf, hdr = hdr, block = block)
    }

    override fun param(
        name: String,
        type: String,
        arity: L_ParamArity,
        exact: Boolean,
        nullable: Boolean,
        lazy: Boolean,
        implies: L_ParamImplication?,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        commonMaker.param(
            name = name,
            type = type,
            arity = arity,
            exact = exact,
            nullable = nullable,
            lazy = lazy,
            implies = implies,
            hdr = hdr,
            block = block,
        )
        paramCount++
        if (arity == L_ParamArity.ONE) requiredParamCount++
    }

    // Count of all declared parameters (typed or string-typed) and of the mandatory ones.
    // A typed param/self delegate indexes into the runtime arg list [receiver?, p0, p1, ...];
    // its index is therefore `receiverSlots + ordinal`, so typed params index correctly even
    // when the receiver type has no token (collections, structs, ...) and `self()` is absent.
    private var paramCount = 0
    private var requiredParamCount = 0

    override fun <T : Rt_Value> self(type: Rt_ValueClass<T>): Ld_ParamRef<T> {
        require(receiverSlots == 1) { "self() is only valid for a member function" }
        return Ld_ParamRef(0, type)
    }

    override fun <T : Rt_Value> param(
        type: Rt_ValueClass<T>,
        exact: Boolean,
        implies: L_ParamImplication?,
        since: String?,
        comment: String?,
    ): Ld_ParamProvider<T> = Ld_ParamProvider { name ->
        val index = receiverSlots + paramCount
        param(
            name = name,
            type = type.name,
            arity = L_ParamArity.ONE,
            exact = exact,
            implies = implies,
            since = since,
            comment = comment,
        )
        Ld_ParamRef(index, type)
    }

    override fun <T : Rt_Value> param(
        type: String,
        cast: Rt_ValueClass<T>,
        exact: Boolean,
        lazy: Boolean,
        nullable: Boolean,
        implies: L_ParamImplication?,
        since: String?,
        comment: String?,
    ): Ld_ParamProvider<T> = Ld_ParamProvider { name ->
        val index = receiverSlots + paramCount
        param(
            name = name,
            type = type,
            arity = L_ParamArity.ONE,
            exact = exact,
            nullable = nullable,
            lazy = lazy,
            implies = implies,
            since = since,
            comment = comment,
        )
        Ld_ParamRef(index, cast)
    }

    override fun <T : Rt_Value> paramOpt(
        type: String,
        cast: Rt_ValueClass<T>,
        exact: Boolean,
        lazy: Boolean,
        nullable: Boolean,
        implies: L_ParamImplication?,
        since: String?,
        comment: String?,
    ): Ld_OptParamProvider<T> = Ld_OptParamProvider { name ->
        val index = receiverSlots + paramCount
        param(
            name = name,
            type = type,
            arity = L_ParamArity.ZERO_ONE,
            exact = exact,
            nullable = nullable,
            lazy = lazy,
            implies = implies,
            since = since,
            comment = comment,
        )
        Ld_OptParamRef(index, cast)
    }

    override fun <T : Rt_Value> paramOpt(
        type: Rt_ValueClass<T>,
        exact: Boolean,
        implies: L_ParamImplication?,
        since: String?,
        comment: String?,
    ): Ld_OptParamProvider<T> = Ld_OptParamProvider { name ->
        val index = receiverSlots + paramCount
        param(
            name = name,
            type = type.name,
            arity = L_ParamArity.ZERO_ONE,
            exact = exact,
            implies = implies,
            since = since,
            comment = comment,
        )
        Ld_OptParamRef(index, type)
    }

    override fun <T : Rt_Value, N : Any> body(returns: Rt_PrimitiveFactory<T, N>, rCode: () -> N): Ld_BodyResult {
        commonMaker.setResultTypeIfAbsent(returns.name)
        val required = receiverSlots + requiredParamCount
        val total = receiverSlots + paramCount
        // Argument-stack push/pop is handled by bodyContextN (the common funnel).
        return bodyN { args ->
            Rt_Utils.checkRange(args.size, required, total)
            returns.wrap(rCode())
        }
    }

    // Parameterless body forms: the argument count is validated against the declared parameters
    // (delegates supply the values), so the body lambda restates no arity.
    override fun body(rCode: () -> Rt_Value): Ld_BodyResult = bodyN { args ->
        Rt_Utils.checkRange(args.size, receiverSlots + requiredParamCount, receiverSlots + paramCount)
        rCode()
    }

    override fun bodyContext(rCode: (Rt_CallContext) -> Rt_Value): Ld_BodyResult = bodyContextN { ctx, args ->
        Rt_Utils.checkRange(args.size, receiverSlots + requiredParamCount, receiverSlots + paramCount)
        rCode(ctx)
    }
}

interface Ld_CommonFunctionMaker: Ld_MemberHeaderMaker {
    fun deprecated(deprecated: C_Deprecated)
    fun generic(name: String, subOf: String?, superOf: String?, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)

    /**
     * Set the declared result type from a typed `body(...)` token, unless an explicit result type
     * was already specified. No-op for constructors (their result type is the constructed type).
     */
    fun setResultTypeIfAbsent(type: String)

    fun param(
        name: String,
        type: String,
        arity: L_ParamArity = L_ParamArity.ONE,
        exact: Boolean,
        nullable: Boolean,
        lazy: Boolean,
        implies: L_ParamImplication?,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    )
}

abstract class Ld_CommonFunctionBuilder(
    hdr: Ld_MemberHeader,
    private val outerTypeParams: ImmSet<Name>,
    private val bodyBuilder: Ld_FunctionBodyBuilder,
): Ld_MemberHeaderBuilder(hdr), Ld_CommonFunctionMaker {
    private var deprecated: C_Deprecated? = null
    private val typeParams = mutableMapOf<Name, Ld_TypeParam>()
    private val params = mutableMapOf<Name, Ld_FunctionParam>()
    private var paramsFinished = false

    final override fun deprecated(deprecated: C_Deprecated) {
        require(this.deprecated == null)
        finishParams()
        this.deprecated = deprecated
    }

    override fun setResultTypeIfAbsent(type: String) {
        // Constructors have no declared result type; Ld_FunctionBuilder overrides this.
    }

    final override fun generic(
        name: String,
        subOf: String?,
        superOf: String?,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        require(subOf == null || superOf == null)
        require(params.isEmpty()) { "Trying to add a type parameter after a function parameter" }
        require(bodyBuilder.isEmpty()) { "Body already set" }

        val typeParam = Ld_TypeParam.make(name, subOf = subOf, superOf = superOf, hdr = hdr, block = block)

        Ld_Exception.check(typeParam.name !in typeParams) {
            "fun:type_param_conflict:$name" to "Name conflict: $name"
        }
        Ld_Exception.check(typeParam.name !in outerTypeParams) {
            "fun:type_param_conflict_outer:$name" to "Name conflict (outer type parameter): $name"
        }

        typeParams[typeParam.name] = typeParam
    }

    final override fun param(
        name: String,
        type: String,
        arity: L_ParamArity,
        exact: Boolean,
        nullable: Boolean,
        lazy: Boolean,
        implies: L_ParamImplication?,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        require(bodyBuilder.isEmpty()) { "Body already set" }

        Ld_Exception.check(!paramsFinished) {
            "common_fun:params_already_defined:$name" to "Parameters already defined"
        }

        val rName = Name.of(name)
        Ld_Exception.check(rName !in params) {
            "common_fun:param_name_conflict:$name" to "Parameter name conflict: $name"
        }

        val memberHeader = Ld_MemberHeader.make(hdr, block)

        val param = Ld_FunctionParam(
            memberHeader = memberHeader,
            name = rName,
            type = Ld_Type.parse(type),
            arity = arity.mArity,
            exact = exact,
            nullable = nullable,
            lazy = lazy,
            implies = implies,
        )

        params[rName] = param
    }

    protected fun finishParams() {
        if (params.isNotEmpty()) {
            paramsFinished = true
        }
    }

    protected fun buildCommon(bodyRes: Ld_BodyResult): Ld_CommonFunction {
        val memberHeader = buildMemberHeader()

        val header = Ld_CommonFunctionHeader(
            typeParams = typeParams.values.toImmList(),
            params = params.values.toImmList(),
        )

        val body = bodyBuilder.build(bodyRes)

        return Ld_CommonFunction(
            memberHeader = memberHeader,
            header = header,
            deprecated = deprecated,
            body = body,
        )
    }
}

class Ld_CommonFunctionHeader(
    val typeParams: ImmList<Ld_TypeParam>,
    val params: ImmList<Ld_FunctionParam>,
)

class Ld_CommonFunction(
    val memberHeader: Ld_MemberHeader,
    val header: Ld_CommonFunctionHeader,
    val deprecated: C_Deprecated?,
    val body: Ld_FunctionBody,
)

class Ld_FunctionParam(
    val memberHeader: Ld_MemberHeader,
    val name: Name,
    private val type: Ld_Type,
    private val arity: M_ParamArity,
    private val exact: Boolean,
    private val nullable: Boolean,
    private val lazy: Boolean,
    private val implies: L_ParamImplication?,
) {
    fun finish(
        ctx: Ld_TypeFinishContext,
        hdr: Ld_MemberHeader.Finish,
        comment: DocComment?,
    ): L_FunctionParam {
        val mType = type.finish(ctx)

        if (nullable) {
            Ld_Exception.check(mType is M_Type_Nullable || mType == M_Types.ANYTHING) {
                "function:param_not_nullable:${mType.strCode()}" to
                        "Parameter marked as nullable, but type is not nullable: ${mType.strMsg()}"
            }
        }

        val mParam = M_FunctionParam(
            name = name.str,
            type = mType,
            arity = arity,
            exact = exact,
            nullable = nullable,
        )

        val restrictions = C_MemberRestrictions.makeLib0(hdr.fullName, C_DeclarationType.PARAMETER, hdr.lHeader, null)

        val rType = L_TypeUtils.getRType(mType)
        val docParam = docFunctionParam(name, rType, arity, exact = exact, nullable = nullable)

        val doc = hdr.docSymbol(
            symbolName = DocSymbolName.local(name.str),
            declaration = DocDeclarationProto_Parameter(docParam, lazy, implies, null).toLazyDeclaration(),
            comment = comment,
        )

        return L_FunctionParam(
            name = name,
            mParam = mParam,
            lazy = lazy,
            implies = implies,
            restrictions = restrictions,
            docSymbol = doc,
        )
    }

    companion object {
        fun finish(
            ctx: Ld_TypeFinishContext,
            fullName: FullName,
            params: List<Ld_FunctionParam>,
            funMemberHeader: L_MemberHeader,
        ): Pair<ImmList<L_FunctionParam>, DocComment?> {
            val paramNames = params.map { it.name }

            val paramHeaders = params.map { param ->
                val paramFullName = fullName.append(param.name)
                param.memberHeader.finish(ctx.modCfg, paramFullName, DocSymbolKind.PARAMETER, requireSince = false)
            }

            val paramComments = params.withIndex().associateNotNullValues { (i, param) ->
                param.name to paramHeaders[i].lHeader.docComment
            }

            val comments = DocFunctionParamComments.make(
                DefinitionName(fullName),
                funMemberHeader.docComment,
                paramNames,
                paramComments,
                DocException.ERROR_TRACKER,
            )

            val lParams = params.mapIndexedToImmList { i, param ->
                val hdr = paramHeaders[i]
                val comment = comments.paramComments[param.name]
                param.finish(ctx, hdr, comment)
            }

            return lParams to comments.functionComment
        }
    }
}
