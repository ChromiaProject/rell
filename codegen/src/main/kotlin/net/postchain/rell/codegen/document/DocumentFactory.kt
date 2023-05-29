package net.postchain.rell.codegen.document

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.*
import net.postchain.rell.codegen.util.BuiltinType

interface DocumentFactory {
    val fileExtension: String
    fun createDocument(moduleName: String): Document
    fun createEntity(className: ClassName, rellEntity: R_EntityDefinition): Entity
    fun createBuiltins(type: BuiltinType): Builtin
    fun createStruct(className: ClassName, rellStruct: R_StructDefinition): Struct
    fun createEnum(className: ClassName, rellEnum: R_EnumDefinition): Enumeration

    fun createQuery(rellQuery: R_QueryDefinition): Query

    fun createOperation(rellOperation: R_OperationDefinition): Operation

    fun getBuiltins(neededObjects: List<ClassName>): List<Builtin>
}
