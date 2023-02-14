package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.section.*
import net.postchain.rell.model.*

class TypescriptDocumentFactory(private val basePackage: String) : DocumentFactory {
    override val fileExtension: String
        get() = "ts"

    override fun createDocument(moduleName: String): Document {
        TODO("Not yet implemented")
    }

    override fun createEntity(className: ClassName, rellEntity: R_EntityDefinition): Entity {
        TODO("Not yet implemented")
    }

    override fun createBuiltins(): List<Entity> {
        TODO("Not yet implemented")
    }

    override fun createStruct(className: ClassName, rellStruct: R_StructDefinition): Struct {
        TODO("Not yet implemented")
    }

    override fun createEnum(className: ClassName, rellEnum: R_EnumDefinition): Enumeration {
        TODO("Not yet implemented")
    }

    override fun createQuery(rellQuery: R_QueryDefinition): Query {
        TODO("Not yet implemented")
    }

    override fun createOperation(rellOperation: R_OperationDefinition): Operation {
        TODO("Not yet implemented")
    }
}