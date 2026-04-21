/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.QualifiedName
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSymbol

class L_Module(
        val moduleName: ModuleName,
        val namespace: L_Namespace,
        val allImports: ImmList<L_Module>,
        override val docSymbol: DocSymbol,
): DocDefinition() {
    override val docSourcePos = null

    fun getTypeDef(qualifiedName: String): L_TypeDef {
        val qName = QualifiedName.of(qualifiedName)
        return getTypeDef(qName)
    }

    fun getTypeDef(qualifiedName: QualifiedName): L_TypeDef {
        val def = getTypeDefOrNull(qualifiedName)
        return checkNotNull(def) { "Type not found: $qualifiedName" }
    }

    fun getTypeDefOrNull(qualifiedName: QualifiedName): L_TypeDef? {
        val def = namespace.getDefOrNull(qualifiedName)
        return def?.getTypeDefOrNull()
    }

    fun getTypeExtensionOrNull(qualifiedName: QualifiedName): L_TypeExtension? {
        val def = namespace.getDefOrNull(qualifiedName)
        return def?.getTypeExtensionOrNull()
    }

    fun getType(name: String): L_Type {
        val typeDef = getTypeDef(name)
        return typeDef.getType()
    }

    fun getStruct(qualifiedName: String): L_Struct {
        val qName = QualifiedName.of(qualifiedName)
        val def = namespace.getDefOrNull(qName)
        val struct = def?.getStructOrNull()
        return checkNotNull(struct) { "Struct not found: $qualifiedName" }
    }

    fun getEnum(qualifiedName: String): L_Enum {
        val qName = QualifiedName.of(qualifiedName)
        val def = namespace.getDefOrNull(qName)
        val enum = def?.getEnumOrNull()
        return checkNotNull(enum) { "Enum not found: $qualifiedName" }
    }

    fun getAbstractTypeDefOrNull(qualifiedName: QualifiedName): L_AbstractTypeDef? {
        val def = namespace.getDefOrNull(qualifiedName)
        return def?.getAbstractTypeDefOrNull()
    }

    override fun getDocMembers0() = namespace.docMembers
}
