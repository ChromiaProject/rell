/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.lmodel.L_Constructor
import net.postchain.rell.base.lmodel.L_ConstructorHeader
import net.postchain.rell.base.lmodel.L_MemberHeader
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmSet
import net.postchain.rell.base.utils.doc.DocComment

interface Ld_ConstructorMaker: Ld_CommonFunctionMaker

class Ld_ConstructorBuilder(
    hdr: Ld_MemberHeader,
    outerTypeParams: ImmSet<R_Name>,
    bodyBuilder: Ld_FunctionBodyBuilder,
): Ld_CommonFunctionBuilder(hdr, outerTypeParams, bodyBuilder), Ld_ConstructorMaker {
    fun build(bodyRes: Ld_BodyResult): Ld_MemberDef<Ld_Constructor> {
        val cf = buildCommon(bodyRes)

        val memberHeader = buildMemberHeader()

        val header = Ld_ConstructorHeader(
            typeParams = cf.header.typeParams,
            params = cf.header.params,
        )

        val constructor = Ld_Constructor(header, cf.deprecated, cf.body)
        return Ld_MemberDef(memberHeader, constructor)
    }
}

class Ld_ConstructorDslImpl(
    conMaker: Ld_ConstructorMaker,
    bodyMaker: Ld_FunctionBodyDsl,
): Ld_CommonFunctionDslImpl(conMaker, bodyMaker), Ld_ConstructorDsl

class Ld_ConstructorHeader(
    private val typeParams: ImmList<Ld_TypeParam>,
    private val params: ImmList<Ld_FunctionParam>,
) {
    class Finish(val lHeader: L_ConstructorHeader, val comment: DocComment?)

    fun finish(ctx: Ld_TypeFinishContext, fullName: R_FullName, memberHeader: L_MemberHeader): Finish {
        val lTypeParams = Ld_TypeParam.finishList(ctx, typeParams)

        val subCtx = ctx.subCtx(lTypeParams.map)
        val (lParams, funComment) = Ld_FunctionParam.finish(subCtx, fullName, params, memberHeader)

        val lHeader = L_ConstructorHeader(typeParams = lTypeParams.list, params = lParams)
        return Finish(lHeader, funComment)
    }
}

class Ld_Constructor(
    private val header: Ld_ConstructorHeader,
    private val deprecated: C_Deprecated?,
    private val body: Ld_FunctionBody,
) {
    class Finish(
        val lConstructor: L_Constructor,
        val comment: DocComment?,
    )

    fun finish(ctx: Ld_TypeFinishContext, fullName: R_FullName, lMemberHeader: L_MemberHeader): Finish {
        val finHeader = header.finish(ctx, fullName, lMemberHeader)
        val lBody = body.finish(fullName.qualifiedName)

        val lConstructor = L_Constructor(
            header = finHeader.lHeader,
            deprecated = deprecated,
            body = lBody,
            pure = body.pure,
        )

        return Finish(lConstructor, finHeader.comment)
    }
}
