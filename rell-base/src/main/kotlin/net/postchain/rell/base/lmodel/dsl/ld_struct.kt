/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.def.C_SysAttribute
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.lmodel.L_Struct
import net.postchain.rell.base.lmodel.L_StructAttribute
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.doc.DocDeclaration_StructAttribute
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.doc.DocSymbolName
import net.postchain.rell.base.utils.futures.FcFuture
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

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

    fun build(): Ld_Struct {
        val memberHeader = memberBuilder.buildMemberHeader()
        return Ld_Struct(memberHeader, attributes.values.toImmList())
    }
}

class Ld_StructAttribute(
    val memberHeader: Ld_MemberHeader,
    val name: R_Name,
    val type: Ld_Type,
    val mutable: Boolean,
) {
    fun finish(ctx: Ld_TypeFinishContext, outerFullName: R_FullName): L_StructAttribute {
        val mType = type.finish(ctx)
        val doc = finishDoc(outerFullName, mType)
        return L_StructAttribute(name, mType, mutable = mutable, docSymbol = doc)
    }

    private fun finishDoc(outerFullName: R_FullName, mType: M_Type): DocSymbol {
        val fullName = outerFullName.append(name)
        val docType = L_TypeUtils.docType(mType)
        return DocSymbol(
            kind = DocSymbolKind.STRUCT_ATTR,
            symbolName = DocSymbolName.global(fullName.moduleName.str(), fullName.qualifiedName.str()),
            mountName = null,
            declaration = DocDeclaration_StructAttribute(fullName.last, docType, mutable),
            comment = memberHeader.docComment(),
        )
    }
}

class Ld_Struct(
    val memberHeader: Ld_MemberHeader,
    private val attributes: List<Ld_StructAttribute>,
) {
    fun process(ctx: Ld_NamespaceContext, fullName: R_FullName): FcFuture<L_Struct> {
        val rStruct = C_Utils.createSysStruct(fullName.qualifiedName.str())

        return ctx.fcExec.future().compute {
            val attributesFuture = ctx.fcExec.future().after(ctx.finishCtxFuture).compute { finishCtx ->
                val lAttributes = attributes
                    .map { it.finish(finishCtx.typeCtx, fullName) }
                    .toImmList()

                val rAttributes = lAttributes
                    .mapIndexed { i, lAttr -> lAttr.name to finishAttr(fullName.qualifiedName, lAttr, i) }
                    .toImmMap()
                rStruct.setAttributes(rAttributes)

                lAttributes.associateBy { it.name.str }.toImmMap()
            }

            L_Struct(fullName.last, rStruct, attributesFuture)
        }
    }

    private fun finishAttr(qualifiedName: R_QualifiedName, lAttr: L_StructAttribute, i: Int): R_Attribute {
        val name = lAttr.name
        val mType = lAttr.type

        val rType = L_TypeUtils.getRType(mType)
        checkNotNull(rType) {
            "Cannot convert type of struct attribute $qualifiedName.$name to R_Type: ${mType.strCode()}"
        }

        val cAttr = C_SysAttribute(name.str, rType, mutable = lAttr.mutable, docSymbol = lAttr.docSymbol)
        return cAttr.compile(i, false)
    }
}
