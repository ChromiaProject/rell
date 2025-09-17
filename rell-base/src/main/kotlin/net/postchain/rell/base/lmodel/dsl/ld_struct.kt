/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.def.C_SysAttribute
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.lmodel.L_Struct
import net.postchain.rell.base.lmodel.L_StructAttribute
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDeclarationProto_StructAttribute
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.futures.FcFuture

class Ld_StructDslImpl(
    hdr: Ld_MemberHeader,
    private val memberBuilder: Ld_MemberHeaderBuilder = Ld_MemberHeaderBuilder(hdr),
): Ld_StructDsl, Ld_MemberDsl by Ld_MemberDslImpl(memberBuilder) {
    private val attributes = mutableMapOf<R_Name, Ld_StructAttribute>()

    override fun attribute(
        name: String,
        type: String,
        mutable: Boolean,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val rName = R_Name.of(name)
        check(rName !in attributes) { "Name conflict: $rName" }

        val hdr0 = Ld_MemberHeader.make(since, comment)
        val memberHeader = Ld_MemberHeader.make(hdr0, block)
        val ldType = Ld_Type.parse(type)
        attributes[rName] = Ld_StructAttribute(memberHeader, rName, ldType, mutable = mutable)
    }

    fun build(): Ld_MemberDef<Ld_Struct> {
        val memberHeader = memberBuilder.buildMemberHeader()
        return Ld_MemberDef(memberHeader, Ld_Struct(attributes.values.toImmList()))
    }
}

class Ld_StructAttribute(
    private val memberHeader: Ld_MemberHeader,
    val name: R_Name,
    val type: Ld_Type,
    val mutable: Boolean,
) {
    fun finish(ctx: Ld_TypeFinishContext, outerFullName: R_FullName): L_StructAttribute {
        val mType = type.finish(ctx)
        val fullName = outerFullName.append(name)
        val hdr = memberHeader.finish(ctx.modCfg, fullName, DocSymbolKind.STRUCT_ATTR, requireSince = false)
        val doc = finishDoc(hdr, mType)
        return L_StructAttribute(fullName, mType, mutable = mutable, header = hdr.lHeader, docSymbol = doc)
    }

    private fun finishDoc(hdr: Ld_MemberHeader.Finish, mType: M_Type): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        val docDec = DocDeclarationProto_StructAttribute(hdr.simpleName, docType, mutable).toLazyDeclaration()
        return hdr.docSymbol(docDec)
    }
}

class Ld_Struct(
    private val attributes: ImmList<Ld_StructAttribute>,
) {
    fun process(ctx: Ld_NamespaceContext, fullName: R_FullName): FcFuture<L_Struct> {
        val rStruct = C_Utils.createSysStruct(fullName.qualifiedName.str())

        return ctx.fcExec.future().compute {
            val attributesFuture = ctx.fcExec.future().after(ctx.finishCtxFuture).compute { finishCtx ->
                val lAttributes = attributes.mapToImmList { it.finish(finishCtx.typeCtx, fullName) }

                val rAttributes = lAttributes
                    .mapIndexed { i, lAttr -> lAttr.simpleName to finishAttr(fullName.qualifiedName, lAttr, i) }
                    .toImmMap()
                rStruct.setAttributes(rAttributes)

                val rTypeFlags = R_TypeFlags(
                    pure = true,
                    mutable = false,
                    gtv = R_GtvCompatibility(fromGtv = true, toGtv = true),
                    virtualable = true,
                    mixedTuple = false,
                )

                rStruct.setFlags(R_StructFlags(typeFlags = rTypeFlags, cyclic = false, infinite = false))

                lAttributes.associateByToImmMap { it.simpleName.str }
            }

            L_Struct(fullName.last, rStruct, attributesFuture)
        }
    }

    private fun finishAttr(qualifiedName: R_QualifiedName, lAttr: L_StructAttribute, i: Int): R_Attribute {
        val name = lAttr.simpleName
        val mType = lAttr.type

        val rType = L_TypeUtils.getRType(mType)
        checkNotNull(rType) {
            "Cannot convert type of struct attribute $qualifiedName.$name to R_Type: ${mType.strCode()}"
        }

        val cAttr = C_SysAttribute(
            name.str,
            rType,
            mutable = lAttr.mutable,
            docSymbol = lAttr.docSymbol,
            restrictions = C_MemberRestrictions.makeLib(lAttr, C_DeclarationType.ATTRIBUTE, null),
        )

        return cAttr.compile(i, false)
    }
}
