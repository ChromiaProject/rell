/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.mtype.M_FunctionParam
import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.mtype.M_Type_Nullable
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*

abstract class Ld_CommonFunctionDslImpl(
    private val commonMaker: Ld_CommonFunctionMaker,
    private val bodyDsl: Ld_FunctionBodyDsl,
): Ld_CommonFunctionDsl, Ld_FunctionBodyDsl by bodyDsl, Ld_MemberDsl by Ld_MemberDslImpl(commonMaker) {
    override val fnSimpleName: String get() = bodyDsl.fnSimpleName

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
    }
}

interface Ld_CommonFunctionMaker: Ld_MemberHeaderMaker {
    fun deprecated(deprecated: C_Deprecated)
    fun generic(name: String, subOf: String?, superOf: String?, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)

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
    private val outerTypeParams: ImmSet<R_Name>,
    private val bodyBuilder: Ld_FunctionBodyBuilder,
): Ld_MemberHeaderBuilder(hdr), Ld_CommonFunctionMaker {
    private var deprecated: C_Deprecated? = null
    private val typeParams = mutableMapOf<R_Name, Ld_TypeParam>()
    private val params = mutableMapOf<R_Name, Ld_FunctionParam>()
    private var paramsFinished = false

    final override fun deprecated(deprecated: C_Deprecated) {
        require(this.deprecated == null)
        finishParams()
        this.deprecated = deprecated
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

        val rName = R_Name.of(name)
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
    val name: R_Name,
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

        val docParam = L_TypeUtils.docFunctionParam(mParam)

        val doc = hdr.docSymbol(
            symbolName = DocSymbolName.local(name.str),
            declaration = DocDeclaration_Parameter(docParam, lazy, implies, null),
            comment = comment,
        )

        val restrictions = C_MemberRestrictions.makeLib0(hdr.fullName, C_DeclarationType.PARAMETER, hdr.lHeader, null)

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
            fullName: R_FullName,
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
                R_DefinitionName(fullName),
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
