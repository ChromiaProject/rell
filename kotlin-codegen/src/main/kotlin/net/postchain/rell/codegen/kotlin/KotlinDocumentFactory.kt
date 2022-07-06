package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.kotlin.util.BlockEntity
import net.postchain.rell.codegen.kotlin.util.TransactionEntity
import net.postchain.rell.model.R_EntityDefinition
import net.postchain.rell.model.R_EnumDefinition
import net.postchain.rell.model.R_QueryDefinition
import net.postchain.rell.model.R_StructDefinition

class KotlinDocumentFactory : DocumentFactory {

    override fun createDocument(packageString: String) = KotlinDocument(packageString)

    override fun createEntity(rellEntity: R_EntityDefinition) = KotlinEntity(rellEntity)

    override fun createBuiltins() = listOf(BlockEntity(), TransactionEntity())

    override fun createStruct(rellStruct: R_StructDefinition) = KotlinStruct(rellStruct)

    override fun createEnum(rellEnum: R_EnumDefinition) = KotlinEnumeration(rellEnum)

    override fun createQuery(rellQuery: R_QueryDefinition) = KotlinQuery(rellQuery)
}