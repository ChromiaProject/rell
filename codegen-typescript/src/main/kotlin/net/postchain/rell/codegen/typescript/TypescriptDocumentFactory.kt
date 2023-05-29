package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.typescript.util.TypescriptBuiltinType
import net.postchain.rell.codegen.util.BuiltinType

class TypescriptDocumentFactory : DocumentFactory {
    override val fileExtension: String
        get() = "ts"

    override fun createDocument(moduleName: String) = TypescriptDocument(moduleName)

    override fun createEntity(className: ClassName, rellEntity: R_EntityDefinition) = TypescriptEntity(className, rellEntity)

    override fun createBuiltins(type: BuiltinType) = type.createBuiltin()

    override fun createStruct(className: ClassName, rellStruct: R_StructDefinition) = TypescriptStruct(className, rellStruct)

    override fun createEnum(className: ClassName, rellEnum: R_EnumDefinition) = TypescriptEnumeration(className, rellEnum)

    override fun createQuery(rellQuery: R_QueryDefinition) = TypescriptQuery(rellQuery)

    override fun createOperation(rellOperation: R_OperationDefinition) = TypescriptOperation(rellOperation)

    override fun getBuiltins(neededObjects: List<ClassName>) = TypescriptBuiltinType.values()
            .filter { it.className in neededObjects.map { x -> x.className } }
            .map { it.createBuiltin() }
}
