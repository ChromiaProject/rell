package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.kotlin.util.BlockEntity
import net.postchain.rell.codegen.kotlin.util.TransactionEntity
import net.postchain.rell.model.*

class KotlinDocumentFactory(private val basePackage: String) : DocumentFactory {
    override val fileExtension: String
        get() = "kt"

    override fun createDocument(moduleName: String) = KotlinDocument(basePackage, moduleName)

    override fun createEntity(className: ClassName, rellEntity: R_EntityDefinition) = KotlinEntity(className, rellEntity)

    override fun createBuiltins() = listOf(BlockEntity(), TransactionEntity())

    override fun createStruct(className: ClassName, rellStruct: R_StructDefinition) = KotlinStruct(className, rellStruct)

    override fun createEnum(className: ClassName, rellEnum: R_EnumDefinition) = KotlinEnumeration(className, rellEnum)

    override fun createQuery(rellQuery: R_QueryDefinition) = KotlinQuery(rellQuery)

    override fun createOperation(rellOperation: R_OperationDefinition) = KotlinOperation(rellOperation)
}