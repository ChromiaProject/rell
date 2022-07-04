package net.postchain.rell.codegen

import net.postchain.rell.model.R_EntityDefinition

interface DocumentFactory {
    fun createDocument(packageString: String): Document

    fun createEntity(rellEntity: R_EntityDefinition): Entity
}