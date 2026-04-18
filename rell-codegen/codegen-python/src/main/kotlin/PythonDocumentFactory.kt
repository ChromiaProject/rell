/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.python.util.PythonBuiltinType
import net.postchain.rell.codegen.util.BuiltinType

class PythonDocumentFactory : DocumentFactory {
    override val fileExtension: String
        get() = "py"

    override fun createDocument(moduleName: String) = PythonDocument(moduleName)

    override fun createEntity(
        className: ClassName,
        rellEntity: R_EntityDefinition
    ) = PythonEntity(className, rellEntity)

    override fun createBuiltins(type: BuiltinType) = type.createBuiltin()

    override fun createStruct(
        className: ClassName,
        rellStruct: R_StructDefinition
    ) = PythonStruct(className, rellStruct)

    override fun createEnum(className: ClassName, rellEnum: R_EnumDefinition) = PythonEnumeration(className, rellEnum)

    override fun createQuery(rellQuery: R_QueryDefinition) = PythonQuery(rellQuery)

    override fun createOperation(rellOperation: R_OperationDefinition) = PythonOperation(rellOperation)

    override fun getBuiltins(neededObjects: List<ClassName>) = PythonBuiltinType.entries
            .filter { it.className in neededObjects.map { x -> x.className } }
            .map { it.createBuiltin() }
}