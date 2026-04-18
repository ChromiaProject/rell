/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.kotlin

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.kotlin.util.KotlinBuiltinType
import net.postchain.rell.codegen.util.BuiltinType

class KotlinDocumentFactory(private val config: KotlinCodeGeneratorConfig) : DocumentFactory {
    override val fileExtension: String
        get() = "kt"

    override fun createDocument(moduleName: String) = KotlinDocument(config.packageName(), moduleName)

    override fun createEntity(className: ClassName, rellEntity: R_EntityDefinition) = KotlinEntity(className, rellEntity)

    override fun createBuiltins(type: BuiltinType) = type.createBuiltin()

    override fun createStruct(className: ClassName, rellStruct: R_StructDefinition) = KotlinStruct(className, rellStruct)

    override fun createEnum(className: ClassName, rellEnum: R_EnumDefinition) = KotlinEnumeration(className, rellEnum)

    override fun createQuery(rellQuery: R_QueryDefinition) = KotlinQuery(rellQuery)

    override fun createOperation(rellOperation: R_OperationDefinition) = KotlinOperation(rellOperation)

    override fun getBuiltins(neededObjects: List<ClassName>) = KotlinBuiltinType.entries
            .filter { it.className in neededObjects.map { x -> x.className } }
            .map { it.createBuiltin() }
}
