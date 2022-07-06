package net.postchain.rell.codegen.document

import net.postchain.rell.codegen.section.Entity
import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.section.Struct
import net.postchain.rell.model.R_EntityDefinition
import net.postchain.rell.model.R_EnumDefinition
import net.postchain.rell.model.R_QueryDefinition
import net.postchain.rell.model.R_StructDefinition

interface DocumentFactory {
    fun createDocument(packageString: String): Document
    fun createEntity(rellEntity: R_EntityDefinition): Entity
    fun createBuiltins(): List<Entity>
    fun createStruct(rellStruct: R_StructDefinition): Struct
    fun createEnum(rellEnum: R_EnumDefinition): Enumeration

    fun createQuery(rellQuery: R_QueryDefinition): Query
}