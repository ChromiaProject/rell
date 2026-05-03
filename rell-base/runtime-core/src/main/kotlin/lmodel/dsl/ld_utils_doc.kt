/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_DocUtils
import net.postchain.rell.base.lmodel.L_ConstantDocSource
import net.postchain.rell.base.lmodel.L_FunctionFlags
import net.postchain.rell.base.lmodel.L_FunctionHeader
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.mapToImmList

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

        val docHeader = L_TypeUtils.docFunctionHeader(header)
        val docParams = header.params.mapToImmList { it.docSymbol.declaration }
        val dec = DocDeclarationProto_Function(docModifiers, hdr.simpleName, docHeader, docParams).toLazyDeclaration()
        return hdr.docSymbol(declaration = dec, comment = comment)
    }

    fun specialFunction(hdr: Ld_MemberHeader.Finish, isStatic: Boolean): DocSymbol {
        val docDecProto = DocDeclarationProto_SpecialFunction(hdr.simpleName, isStatic = isStatic)
        return hdr.docSymbol(docDecProto.toLazyDeclaration())
    }

    fun constant(hdr: Ld_MemberHeader.Finish, rType: R_Type, docSource: L_ConstantDocSource): DocSymbol {
        val docType = rType.docType()
        val docValue = docSourceToDocValue(docSource)
        val docDecProto = DocDeclarationProto_Constant(DocModifiers.NONE, hdr.simpleName, docType, docValue)
        return hdr.docSymbol(docDecProto.toLazyDeclaration())
    }

    fun property(hdr: Ld_MemberHeader.Finish, rType: R_Type, pure: Boolean): DocSymbol {
        val docType = rType.docType()
        val docDec = DocDeclarationProto_Property(hdr.simpleName, docType, pure).toLazyDeclaration()
        return hdr.docSymbol(docDec)
    }

    fun docSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: Lazy<DocDeclaration>,
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

private fun docSourceToDocValue(source: L_ConstantDocSource): DocValue? = when (source) {
    L_ConstantDocSource.Null -> DocValue.NULL
    L_ConstantDocSource.Unit -> DocValue.UNIT
    is L_ConstantDocSource.Bool -> DocValue.boolean(source.value)
    is L_ConstantDocSource.Int -> DocValue.integer(source.value)
    is L_ConstantDocSource.BigInt -> DocValue.bigInteger(source.value)
    is L_ConstantDocSource.Decimal -> DocValue.decimal(source.value)
    is L_ConstantDocSource.Text -> DocValue.text(source.value)
    is L_ConstantDocSource.Bytes -> DocValue.byteArray(source.value)
    is L_ConstantDocSource.Rowid -> DocValue.rowid(source.value)
    is L_ConstantDocSource.Complex -> null
}
