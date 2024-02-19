@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.model

import com.chromia.rell.dokka.translator.RellDeclarationDescriptor
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_Definition
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_EnumAttr
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_FunctionDefinition
import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.model.R_GlobalConstantDefinition
import net.postchain.rell.base.model.R_Module
import net.postchain.rell.base.model.R_ObjectDefinition
import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_RoutineDefinition
import net.postchain.rell.base.model.R_StructDefinition
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.analysis.kotlin.descriptors.compiler.configuration.DescriptorDocumentableSource
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DriTarget
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DEnumEntry
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.Dynamic
import org.jetbrains.dokka.model.KotlinModifier
import org.jetbrains.dokka.model.KotlinVisibility
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text

// Module == kotlin package
fun R_App.definitionsByModule(): Map<R_Module, RellModule> {
    return modules.associateWith { m ->
        RellModule(
                m.operations.values,
                m.queries.values,
                m.functions.values,
                m.constants.values,
                m.entities.values,
                m.structs.values,
                m.enums.values,
                m.objects.values
        )
    }
}

fun R_Module.toDRI() = DRI(name.str())

fun R_Definition.toDRI() = DRI(cDefName.module.str(), simpleName)


private fun R_RoutineDefinition.toDFunction(
        sourceSet: DokkaConfiguration.DokkaSourceSet,
        documentationNode: DocumentationNode,
        modifier: KotlinModifier? = null
): DFunction = DFunction(
        dri = toDRI(),
        name = simpleName,
        isConstructor = false,
        parameters = params().mapIndexed { i, v -> v.toDFunction(sourceSet, toDRI(), i) },
        expectPresentInSet = null,
        visibility = mapOf(),
        receiver = null,
        isExpectActual = false,
        type = Dynamic,
        sourceSets = setOf(sourceSet),
        generics = listOf(),
        sources = mapOf(sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())),
        documentation = mapOf(sourceSet to documentationNode),
        modifier = if (modifier != null) mapOf(sourceSet to modifier) else mapOf()
)

fun R_OperationDefinition.toDFunction(sourceSet: DokkaConfiguration.DokkaSourceSet) =
        toDFunction(
                sourceSet,
                DocumentationNode(listOf(Description(Text("This operation is called $simpleName")))),
                KotlinModifier.Sealed
        )

fun R_QueryDefinition.toDFunction(sourceSet: DokkaConfiguration.DokkaSourceSet) =
        toDFunction(
                sourceSet,
                DocumentationNode(listOf(Description(Text("This query is called $simpleName")))),
                KotlinModifier.Open
        )

fun R_FunctionDefinition.toDFunction(sourceSet: DokkaConfiguration.DokkaSourceSet) =
        toDFunction(
                sourceSet,
                DocumentationNode(listOf(Description(Text("This function is called $simpleName"))))
        )

fun R_FunctionParam.toDFunction(sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI, index: Int) = DParameter(
        dri = parent.copy(target = PointingToCallableParameters(index)),
        name = name.str,
        expectPresentInSet = null,
        type = Dynamic,
        sourceSets = setOf(sourceSet),
        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("This parameter is called $name"))))),
)

fun R_GlobalConstantDefinition.toDProperty(sourceSet: DokkaConfiguration.DokkaSourceSet) = DProperty(
        dri = DRI(this.defName.module, this.simpleName),
        name = simpleName,
        receiver = null,
        setter = null,
        getter = null,
        visibility = mapOf(),
        modifier = mapOf(),
        generics = listOf(),
        isExpectActual = false,
        sourceSets = setOf(sourceSet),
        sources = mapOf(sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())),
        type = Dynamic,
        expectPresentInSet = null,
        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("This is constant $simpleName")))))
)

fun R_Attribute.toDProperty(sourceSet: DokkaConfiguration.DokkaSourceSet, parent: DRI, index: Int) = DProperty(
        dri = parent.copy(target = PointingToCallableParameters(index)),
        name = name,
        receiver = null,
        setter = null,
        getter = null,
        visibility = mapOf(),
        modifier = mapOf(),
        generics = listOf(),
        isExpectActual = false,
        sourceSets = setOf(sourceSet),
        sources = mapOf(sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())),
        type = Dynamic,
        expectPresentInSet = null,
        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("This is property $name")))))
)

fun R_EntityDefinition.toDClasslike(sourceSet: DokkaConfiguration.DokkaSourceSet) = DInterface(
        dri = toDRI(),
        name = this.simpleName,
        functions = listOf(),
        properties = attributes.values.mapIndexed { i, v -> v.toDProperty(sourceSet, toDRI(), i) },
        classlikes = listOf(),
        visibility = mapOf(sourceSet to KotlinVisibility.Public),
        companion = null,
        generics = listOf(),
        supertypes = mapOf(),
        isExpectActual = false,
        sources = mapOf(sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())),
        expectPresentInSet = null,
        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("This entity is called $simpleName"))))),
        sourceSets = setOf(sourceSet)
)

fun R_StructDefinition.toDClasslike(sourceSet: DokkaConfiguration.DokkaSourceSet) = DInterface(
        dri = toDRI(),
        name = this.simpleName,
        functions = listOf(),
        properties = struct.attributes.values.mapIndexed { i, v -> v.toDProperty(sourceSet, toDRI(), i) },
        classlikes = listOf(),
        visibility = mapOf(sourceSet to KotlinVisibility.Internal),
        companion = null,
        generics = listOf(),
        supertypes = mapOf(),
        isExpectActual = false,
        sources = mapOf(sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())),
        expectPresentInSet = null,
        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("This struct is called $simpleName"))))),
        sourceSets = setOf(sourceSet)
)

fun R_ObjectDefinition.toDClasslike(sourceSet: DokkaConfiguration.DokkaSourceSet) = DInterface(
        dri = toDRI(),
        name = this.simpleName,
        functions = listOf(),
        properties = this.rEntity.attributes.values.mapIndexed { i, v -> v.toDProperty(sourceSet, toDRI(), i) },
        classlikes = listOf(),
        visibility = mapOf(sourceSet to KotlinVisibility.Protected),
        companion = null,
        generics = listOf(),
        supertypes = mapOf(),
        isExpectActual = false,
        sources = mapOf(sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())),
        expectPresentInSet = null,
        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("This struct is called $simpleName"))))),
        sourceSets = setOf(sourceSet)
)

fun R_EnumDefinition.toDClasslike(sourceSet: DokkaConfiguration.DokkaSourceSet) = DEnum(
        dri = toDRI(),
        name = simpleName,
        functions = listOf(),
        constructors = listOf(),
        properties = listOf(),
        entries = attrs.map { it.toDEnumEntry(defName.module, sourceSet) },
        classlikes = listOf(),
        visibility = mapOf(sourceSet to KotlinVisibility.Protected),
        companion = null,
        supertypes = mapOf(),
        isExpectActual = false,
        sources = mapOf(sourceSet to DescriptorDocumentableSource(RellDeclarationDescriptor())),
        expectPresentInSet = null,
        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("This struct is called $simpleName"))))),
        sourceSets = setOf(sourceSet)
)

fun R_EnumAttr.toDEnumEntry(module: String, sourceSet: DokkaConfiguration.DokkaSourceSet) = DEnumEntry(
        dri = DRI(module, name),
        name = name,
        classlikes = listOf(),
        expectPresentInSet = null,
        functions = listOf(),
        properties = listOf(),
        sourceSets = setOf(sourceSet),
        documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("Enum entry $name")))))
)

