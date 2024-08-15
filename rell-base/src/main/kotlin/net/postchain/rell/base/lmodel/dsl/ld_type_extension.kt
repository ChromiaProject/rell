/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.doc.DocDeclaration_TypeExtension
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.futures.FcFuture
import net.postchain.rell.base.utils.futures.component1
import net.postchain.rell.base.utils.futures.component2
import net.postchain.rell.base.utils.immListOf

class Ld_NamespaceMember_TypeExtension(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val type: Ld_Type,
    private val typeDef: Ld_TypeDef,
): Ld_NamespaceMember(DocSymbolKind.TYPE_EXTENSION, simpleName, memberHeader) {
    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        val resultF = typeDef.process(ctx, hdr)
        return ctx.fcExec.future()
            .after(ctx.finishCtxFuture)
            .after(resultF)
            .delegate { (finCtx, result) ->
                finish(finCtx, hdr, result.typeDef, result.membersFuture)
            }
    }

    private fun finish(
        ctx: Ld_NamespaceFinishContext,
        hdr: Ld_MemberHeader.Finish,
        lTypeDef: L_TypeDef,
        membersF: FcFuture<L_TypeDefMembers>,
    ): FcFuture<List<L_NamespaceMember>> {
        val typeParams = lTypeDef.mGenericType.params.associate { R_Name.of(it.name) to M_Types.param(it) }
        val typeCtx = ctx.typeCtx.subCtx(typeParams)
        val mSelfType = type.finish(typeCtx)

        val docTypeParams = L_TypeUtils.docTypeParams(lTypeDef.mGenericType.params)
        val docSelfType = L_TypeUtils.docType(mSelfType)
        val docSymbol = hdr.docSymbol(DocDeclaration_TypeExtension(hdr.simpleName, docTypeParams, docSelfType))

        return ctx.fcExec.future().after(membersF).compute { members ->
            val lTypeExt = L_TypeExtension(
                hdr.fullName.qualifiedName,
                lTypeDef.mGenericType.params,
                mSelfType,
                members,
                docSymbol,
            )

            val member = L_NamespaceMember_TypeExtension(hdr.fullName, hdr.lHeader, lTypeExt)
            immListOf(member)
        }
    }
}
