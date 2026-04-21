/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.lmodel.L_Module
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.associateByToImmMap

class C_LibModule constructor(
    val lModule: L_Module,
    typeDefs: List<C_LibTypeDef>,
    val namespace: C_LibNamespace,
    val extensionTypes: ImmList<C_LibTypeExtension>,
) {
    private val typeDefsByName = typeDefs.associateByToImmMap { it.typeName }

    fun getTypeDef(name: String): C_LibTypeDef {
        return typeDefsByName.getValue(name)
    }

    companion object
}
