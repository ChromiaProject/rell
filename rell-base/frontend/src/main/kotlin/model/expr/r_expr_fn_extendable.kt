/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.R_FunctionBase
import net.postchain.rell.base.model.R_FunctionDefinition
import net.postchain.rell.base.model.R_MapType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals

class R_ExtendableFunctionUid(val id: Int, val name: String) {
    // No equals/hashCode on purpose.
    override fun toString() = "$id:$name"
}

class R_FunctionExtension(val fnBase: R_FunctionBase) {
    override fun toString() = fnBase.toString()
}

class R_FunctionExtensions(
    val uid: R_ExtendableFunctionUid,
    val extensions: ImmList<R_FunctionExtension>,
) {
    override fun toString() = uid.toString()
}

class R_FunctionExtensionsTable(val list: ImmList<R_FunctionExtensions>) {
    init {
        for ((i, c) in this.list.withIndex()) {
            checkEquals(c.uid.id, i)
        }
    }

    fun allExtensions(): List<R_FunctionExtensions> = list
}

class R_ExtendableFunctionDescriptor(
        val uid: R_ExtendableFunctionUid,
        val combiner: R_ExtendableFunctionCombiner
)

sealed class R_ExtendableFunctionCombiner

object R_ExtendableFunctionCombiner_Unit: R_ExtendableFunctionCombiner()
object R_ExtendableFunctionCombiner_Boolean: R_ExtendableFunctionCombiner()
object R_ExtendableFunctionCombiner_Nullable: R_ExtendableFunctionCombiner()
class R_ExtendableFunctionCombiner_List(val type: R_Type): R_ExtendableFunctionCombiner()
class R_ExtendableFunctionCombiner_Map(val mapType: R_MapType): R_ExtendableFunctionCombiner()

class R_FunctionCallTarget_ExtendableUserFunction(
    val baseFn: R_FunctionDefinition,
    val descriptor: R_ExtendableFunctionDescriptor,
): R_FunctionCallTarget()
