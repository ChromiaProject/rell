/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.doc.DocDeclaration_TypeExtension
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.doc.DocSymbolName
import net.postchain.rell.base.utils.futures.FcFuture
import net.postchain.rell.base.utils.futures.component1
import net.postchain.rell.base.utils.futures.component2
import net.postchain.rell.base.utils.immListOf

class Ld_NamespaceMember_TypeExtension(
    simpleName: R_Name,
    private val type: Ld_Type,
    private val typeDef: Ld_TypeDef,
): Ld_NamespaceMember(simpleName) {
    override fun process(ctx: Ld_NamespaceContext): FcFuture<List<L_NamespaceMember>> {
        val fullName = ctx.getFullName(simpleName)
        val resultF = typeDef.process(ctx, fullName)
        return ctx.fcExec.future()
            .after(ctx.finishCtxFuture)
            .after(resultF)
            .delegate { (finCtx, result) ->
                finish(finCtx, fullName, result.memberHeader, result.typeDef, result.membersFuture)
            }
    }

    private fun finish(
        ctx: Ld_NamespaceFinishContext,
        fullName: R_FullName,
        memberHeader: L_MemberHeader,
        lTypeDef: L_TypeDef,
        membersF: FcFuture<L_TypeDefMembers>,
    ): FcFuture<List<L_NamespaceMember>> {
        val typeParams = lTypeDef.mGenericType.params.associate { R_Name.of(it.name) to M_Types.param(it) }
        val typeCtx = ctx.typeCtx.subCtx(typeParams)
        val mSelfType = type.finish(typeCtx)

        val docTypeParams = L_TypeUtils.docTypeParams(lTypeDef.mGenericType.params)
        val docSelfType = L_TypeUtils.docType(mSelfType)
        val docSymbol = Ld_DocSymbols.docSymbol(
            kind = DocSymbolKind.TYPE_EXTENSION,
            symbolName = DocSymbolName.global(fullName),
            declaration = DocDeclaration_TypeExtension(fullName.last, docTypeParams, docSelfType),
            comment = memberHeader.docComment,
        )

        return ctx.fcExec.future().after(membersF).compute { members ->
            val lTypeExt = L_TypeExtension(
                fullName.qualifiedName,
                lTypeDef.mGenericType.params,
                mSelfType,
                members,
                docSymbol,
            )

            val member = L_NamespaceMember_TypeExtension(fullName, memberHeader, lTypeExt)
            immListOf(member)
        }
    }
}
