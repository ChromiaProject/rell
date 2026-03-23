/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.javascript

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.util.BuiltinType

class JavascriptDocumentFactory : DocumentFactory {
    override val fileExtension: String
        get() = "js"

    override fun createDocument(moduleName: String) = JavascriptDocument(moduleName)

    //TODO: Enabling building a mapping from Rell Entity -> Javascript object would help Javascript users
    override fun createEntity(className: ClassName, rellEntity: R_EntityDefinition) =
            throw NotImplementedError("Not needed for Javascript")

    override fun createBuiltins(type: BuiltinType) = type.createBuiltin()

    //TODO: Enabling building a mapping from Rell Struct -> Javascript object would help Javascript users
    override fun createStruct(className: ClassName, rellStruct: R_StructDefinition) =
            throw NotImplementedError("Not needed for Javascript")

    //TODO: Enabling building a mapping from Rell Enum -> Javascript object would help Javascript users
    override fun createEnum(className: ClassName, rellEnum: R_EnumDefinition) =
            throw NotImplementedError("Not needed for Javascript")

    override fun createQuery(rellQuery: R_QueryDefinition) = JavascriptQuery(rellQuery)

    override fun createOperation(rellOperation: R_OperationDefinition) = JavascriptOperation(rellOperation)

    override fun getBuiltins(neededObjects: List<ClassName>) = JavascriptBuiltinType.values()
            .filter { it.builtin.functionName in neededObjects.map { x -> x.className } }
            .map { it.createBuiltin() }
}
