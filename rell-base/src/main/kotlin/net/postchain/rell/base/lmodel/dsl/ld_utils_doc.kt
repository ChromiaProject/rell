/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_DocUtils
import net.postchain.rell.base.lmodel.L_FunctionFlags
import net.postchain.rell.base.lmodel.L_FunctionHeader
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.toImmList

object Ld_DocSymbols {
    fun function(
        hdr: Ld_MemberHeader.Finish,
        header: L_FunctionHeader,
        flags: L_FunctionFlags,
        deprecated: C_Deprecated?,
        comment: DocComment?,
    ): DocSymbol {
        val docModifiers = DocModifiers.make(
            C_DocUtils.docModifier(deprecated),
            if (flags.isPure) DocModifier.PURE else null,
            if (flags.isStatic) DocModifier.STATIC else null,
        )

        val docHeader = L_TypeUtils.docFunctionHeader(header.mHeader)
        val docParams = header.params.map { it.docSymbol.declaration }.toImmList()
        val dec = DocDeclaration_Function(docModifiers, hdr.simpleName, docHeader, docParams)
        return hdr.docSymbol(declaration = dec, comment = comment)
    }

    fun specialFunction(hdr: Ld_MemberHeader.Finish, isStatic: Boolean): DocSymbol {
        return hdr.docSymbol(DocDeclaration_SpecialFunction(hdr.simpleName, isStatic = isStatic))
    }

    fun constant(hdr: Ld_MemberHeader.Finish, mType: M_Type, rValue: Rt_Value): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        val docValue = C_DocUtils.docValue(rValue)
        return hdr.docSymbol(DocDeclaration_Constant(DocModifiers.NONE, hdr.simpleName, docType, docValue))
    }

    fun property(hdr: Ld_MemberHeader.Finish, mType: M_Type, pure: Boolean): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        return hdr.docSymbol(DocDeclaration_Property(hdr.simpleName, docType, pure))
    }

    fun docSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: DocDeclaration,
        comment: DocComment?,
        mountName: String? = null,
    ): DocSymbol {
        return DocSymbol(
            kind = kind,
            symbolName = symbolName,
            mountName = mountName,
            declaration = declaration,
            comment = comment,
        )
    }
}
