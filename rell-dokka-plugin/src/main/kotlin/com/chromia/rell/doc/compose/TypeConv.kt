/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.compose

import com.chromia.rell.doc.model.Doc_Type
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.*

/** Project an `R_Type` (the runtime/compiler type) into the renderer's `Doc_Type`. */
internal fun R_Type.toDocType(): Doc_Type = mType.toDocType()

/**
 * Project an `M_Type` into a `Doc_Type`. The qname assignment here drives cross-references — we
 * use the generic-type's name (with `:` package separator rewritten to `.`) for stdlib types,
 * and `null` for type parameters / unresolved bounds (which can't be hyperlinked).
 */
internal fun M_Type.toDocType(): Doc_Type = when (val t = this) {
    is M_Type_Generic -> {
        val raw = t.genericType.name
        val qname = if (":" in raw) raw.replace(':', '.') else raw
        Doc_Type.Named(
            text = qname,
            qname = qname,
            args = t.typeArgs.map { it.toDocArg() },
        )
    }

    is M_Type_Tuple -> Doc_Type.Tuple(
        t.fieldTypes.mapIndexed { i, ft ->
            Doc_Type.Tuple.Field(t.fieldNames.getOrNull(i), ft.toDocType())
        }
    )

    is M_Type_Function -> Doc_Type.Function(
        t.paramTypes.map { it.toDocType() },
        t.resultType.toDocType(),
    )

    is M_Type_Nullable -> Doc_Type.Nullable(t.valueType.toDocType())

    is M_Type_Param -> Doc_Type.TypeParam(t.param.name)

    is M_Type_Simple -> Doc_Type.Raw(t.strCode())

    else -> Doc_Type.Raw(t.strCode())
}

private fun M_TypeSet.toDocArg(): Doc_Type.Arg = when (val s = this) {
    is M_TypeSet_One -> Doc_Type.Arg.Invariant(s.type.toDocType())
    is M_TypeSet_SubOf -> Doc_Type.Arg.SubOf(s.boundType.toDocType())
    is M_TypeSet_SuperOf -> Doc_Type.Arg.SuperOf(s.boundType.toDocType())
    else -> Doc_Type.Arg.Star
}
