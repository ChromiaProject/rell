/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.utils.ImmList

class DocFunctionParam(
    val name: String,
    val type: DocType,
    val arity: M_ParamArity,
    val exact: Boolean,
    val nullable: Boolean,
)

internal class DocFunctionHeader(
    val typeParams: ImmList<DocTypeParam>,
    val resultType: DocType,
    val params: ImmList<DocFunctionParam>,
)
