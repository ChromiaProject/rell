package net.postchain.rell.codegen

import net.postchain.rell.model.R_EntityDefinition
import net.postchain.rell.model.R_EnumDefinition
import net.postchain.rell.model.R_StructDefinition

interface DocumentFactory {
    fun createDocument(packageString: String): Document
    fun createEntity(rellEntity: R_EntityDefinition): Entity
    fun createBuiltins(): List<Entity>
    fun createStruct(rellStruct: R_StructDefinition): Struct
    fun createEnum(rellEnum: R_EnumDefinition): Enumeration
}