package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.*
import net.postchain.rell.model.R_EntityDefinition
import net.postchain.rell.model.R_EnumDefinition
import net.postchain.rell.model.R_StructDefinition

class KotlinDocumentFactory : DocumentFactory {

    override fun createDocument(packageString: String): Document {
        return KotlinDocument(packageString)
    }

    override fun createEntity(rellEntity: R_EntityDefinition): Entity {
        return KotlinEntity(rellEntity)
    }

    override fun createStruct(rellStruct: R_StructDefinition): Struct {
        return KotlinStruct(rellStruct)
    }

    override fun createEnum(rellEnum: R_EnumDefinition): Enumeration {
        return KotlinEnumeration(rellEnum)
    }
}