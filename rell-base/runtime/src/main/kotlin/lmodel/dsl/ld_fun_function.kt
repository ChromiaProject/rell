/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmSet
import net.postchain.rell.base.utils.doc.DocComment

interface Ld_FunctionMaker: Ld_CommonFunctionMaker {
    fun alias(name: String, deprecated: C_MessageType?, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)
    fun result(type: String)
}

class Ld_FunctionDslImpl(
    private val funMaker: Ld_FunctionMaker,
    bodyMaker: Ld_FunctionBodyDsl,
): Ld_CommonFunctionDslImpl(funMaker, bodyMaker), Ld_FunctionDsl {
    override fun result(type: String) {
        funMaker.result(type)
    }

    override fun alias(
        name: String,
        deprecated: C_MessageType?,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        funMaker.alias(name, deprecated, hdr, block)
    }
}

class Ld_FunctionBuilder(
        hdr: Ld_MemberHeader,
        simpleName: Name,
        outerTypeParams: ImmSet<Name>,
        bodyBuilder: Ld_FunctionBodyBuilder,
): Ld_CommonFunctionBuilder(hdr, outerTypeParams, bodyBuilder), Ld_FunctionMaker {
    private val aliasesBuilder = Ld_AliasesBuilder(simpleName)
    private var resultType: Ld_Type? = null

    override fun alias(name: String, deprecated: C_MessageType?, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit) {
        finishParams()
        val memberHeader = Ld_MemberHeader.make(hdr, block)
        aliasesBuilder.alias(name, deprecated, memberHeader)
    }

    override fun result(type: String) {
        Ld_Exception.check(resultType == null) {
            "function:result_already_defined:$type" to "Result type already set"
        }
        finishParams()
        resultType = Ld_Type.parse(type)
    }

    fun build(bodyRes: Ld_BodyResult): Ld_MemberDef<Ld_Function> {
        val cf = buildCommon(bodyRes)

        val header = Ld_FunctionHeader(
            typeParams = cf.header.typeParams,
            resultType = requireNotNull(resultType) { "Result type not set" },
            params = cf.header.params,
        )

        val fn = Ld_Function(
            aliases = aliasesBuilder.build(),
            header = header,
            deprecated = cf.deprecated,
            body = cf.body,
        )

        return Ld_MemberDef(cf.memberHeader, fn)
    }

    companion object {
        fun build(
                hdr: Ld_MemberHeader,
                simpleName: Name,
                result: String?,
                pure: Boolean?,
                outerTypeParams: ImmSet<Name>,
                block: Ld_FunctionDsl.() -> Ld_BodyResult,
        ): Ld_MemberDef<Ld_Function> {
            val bodyBuilder = Ld_FunctionBodyBuilder(simpleName, pure)
            val funBuilder = Ld_FunctionBuilder(hdr, simpleName, outerTypeParams, bodyBuilder)
            val bodyDslBuilder = Ld_FunctionBodyDslImpl(bodyBuilder)
            val dsl = Ld_FunctionDslImpl(funBuilder, bodyDslBuilder)

            if (result != null) {
                dsl.result(result)
            }

            val bodyRes = block(dsl)
            return funBuilder.build(bodyRes)
        }
    }
}

class Ld_FunctionHeader(
    private val typeParams: ImmList<Ld_TypeParam>,
    private val resultType: Ld_Type,
    private val params: ImmList<Ld_FunctionParam>,
) {
    class Finish(val lHeader: L_FunctionHeader, val comment: DocComment?)

    fun finish(ctx: Ld_TypeFinishContext, fullName: FullName, funMemberHeader: L_MemberHeader): Finish {
        val lTypeParams = Ld_TypeParam.finishList(ctx, typeParams)
        val subCtx = ctx.subCtx(typeParams = lTypeParams.map)

        val mResultType = resultType.finish(subCtx)
        val (lParams, funComment) = Ld_FunctionParam.finish(subCtx, fullName, params, funMemberHeader)

        val intHeader = L_InternalFunctionHeader(lTypeParams.list, mResultType, lParams)
        val lHeader = L_FunctionHeader(intHeader)
        return Finish(lHeader, funComment)
    }
}

class Ld_Function(
    val aliases: ImmList<Ld_Alias>,
    val deprecated: C_Deprecated?,
    private val header: Ld_FunctionHeader,
    private val body: Ld_FunctionBody,
) {
    class Finish(
        val lFunction: L_Function,
        val comment: DocComment?,
    )

    fun finish(
        ctx: Ld_TypeFinishContext,
        fullName: FullName,
        lMemberHeader: L_MemberHeader,
        isStatic: Boolean,
    ): Finish {
        val finHeader = header.finish(ctx, fullName, lMemberHeader)
        val lBody = body.finish(fullName.qualifiedName)
        val lFunction = L_Function(
            fullName,
            header = finHeader.lHeader,
            body = lBody,
            flags = L_FunctionFlags(isPure = body.pure, isStatic = isStatic),
        )
        return Finish(lFunction, finHeader.comment)
    }
}
