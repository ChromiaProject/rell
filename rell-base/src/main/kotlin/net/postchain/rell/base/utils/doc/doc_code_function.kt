/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.mtype.M_ParamArity

class DocFunctionParam(
    val name: String,
    val type: DocType,
    val arity: M_ParamArity,
    val exact: Boolean,
    val nullable: Boolean,
)

class DocFunctionHeader(
    val typeParams: List<DocTypeParam>,
    val resultType: DocType,
    val params: List<DocFunctionParam>,
)
