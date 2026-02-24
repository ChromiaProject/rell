/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.utils.ImmList

sealed class M_AstType

class M_AstType_Name(val name: String): M_AstType()
class M_AstType_Nullable(val valueType: M_AstType): M_AstType()
class M_AstType_Tuple(val fields: ImmList<Pair<String?, M_AstType>>): M_AstType()
class M_AstType_Function(val result: M_AstType, val params: ImmList<M_AstType>): M_AstType()
class M_AstType_Generic(val name: String, val args: ImmList<M_AstTypeSet>): M_AstType()

sealed class M_AstTypeSet
data object M_AstTypeSet_All: M_AstTypeSet()
class M_AstTypeSet_One(val type: M_AstType): M_AstTypeSet()
class M_AstTypeSet_SubOf(val type: M_AstType): M_AstTypeSet()
class M_AstTypeSet_SuperOf(val type: M_AstType): M_AstTypeSet()
