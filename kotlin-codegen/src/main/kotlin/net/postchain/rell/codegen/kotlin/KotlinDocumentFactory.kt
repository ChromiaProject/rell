package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.Document
import net.postchain.rell.codegen.DocumentFactory
import net.postchain.rell.codegen.Entity
import net.postchain.rell.model.R_EntityDefinition

class KotlinDocumentFactory : DocumentFactory {

    fun createDocument(packageString: String): Document {
        return KotlinDocument(packageString)
    }

    fun createEntity(rellEntity: R_EntityDefinition): Entity {
        return KotlinEntity(rellEntity)
    }
}