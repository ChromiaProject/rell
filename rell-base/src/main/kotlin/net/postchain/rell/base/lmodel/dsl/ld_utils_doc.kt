/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_DocUtils
import net.postchain.rell.base.lmodel.L_FunctionFlags
import net.postchain.rell.base.lmodel.L_FunctionHeader
import net.postchain.rell.base.lmodel.L_MemberHeader
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.toImmList

object Ld_DocSymbols {
    fun function(
        fullName: R_FullName,
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
        val dec = DocDeclaration_Function(docModifiers, fullName.last, docHeader, docParams)

        return docSymbol(
            kind = DocSymbolKind.FUNCTION,
            symbolName = DocSymbolName.global(fullName),
            mountName = null,
            declaration = dec,
            comment = comment,
        )
    }

    fun specialFunction(fullName: R_FullName, memberHeader: L_MemberHeader, isStatic: Boolean): DocSymbol {
        return docSymbol(
            kind = DocSymbolKind.FUNCTION,
            symbolName = DocSymbolName.global(fullName),
            mountName = null,
            declaration = DocDeclaration_SpecialFunction(fullName.last, isStatic = isStatic),
            comment = memberHeader.docComment,
        )
    }

    fun constant(fullName: R_FullName, memberHeader: L_MemberHeader, mType: M_Type, rValue: Rt_Value): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        val docValue = C_DocUtils.docValue(rValue)
        val dec = DocDeclaration_Constant(DocModifiers.NONE, fullName.last, docType, docValue)

        return docSymbol(
            kind = DocSymbolKind.CONSTANT,
            symbolName = DocSymbolName.global(fullName),
            mountName = null,
            declaration = dec,
            comment = memberHeader.docComment,
        )
    }

    fun property(fullName: R_FullName, memberHeader: L_MemberHeader, mType: M_Type, pure: Boolean): DocSymbol {
        val docType = L_TypeUtils.docType(mType)
        return docSymbol(
            kind = DocSymbolKind.PROPERTY,
            symbolName = DocSymbolName.global(fullName),
            mountName = null,
            declaration = DocDeclaration_Property(fullName.last, docType, pure),
            comment = memberHeader.docComment,
        )
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
