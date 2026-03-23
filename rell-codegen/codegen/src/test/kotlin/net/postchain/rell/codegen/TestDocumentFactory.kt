/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.section.Entity
import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.codegen.section.Operation
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.section.Struct
import net.postchain.rell.codegen.util.BuiltinType

class TestDocumentFactory: DocumentFactory {
    override val fileExtension = "tst"

    override fun createDocument(moduleName: String) = TestDocument()

    override fun createEntity(className: ClassName, rellEntity: R_EntityDefinition): Entity = TODO("Not yet implemented")
    override fun createBuiltins(type: BuiltinType): Builtin = TODO("Not yet implemented")
    override fun createStruct(className: ClassName, rellStruct: R_StructDefinition): Struct = TODO("Not yet implemented")
    override fun createEnum(className: ClassName, rellEnum: R_EnumDefinition): Enumeration = TODO("Not yet implemented")
    override fun createQuery(rellQuery: R_QueryDefinition): Query = TODO("Not yet implemented")
    override fun createOperation(rellOperation: R_OperationDefinition): Operation = TODO("Not yet implemented")
    override fun getBuiltins(neededObjects: List<ClassName>): List<Builtin> = TODO("Not yet implemented")
}
