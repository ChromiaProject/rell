package com.chromia.rell.dokka.translators

import com.chromia.rell.dokka.descriptors.NULL_DESCRIPTOR
import com.chromia.rell.dokka.doc.simpleDocumentationNode
import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.model.IsEntity
import com.chromia.rell.dokka.model.IsIndex
import com.chromia.rell.dokka.model.IsKey
import com.chromia.rell.dokka.model.IsObject
import com.chromia.rell.dokka.model.IsStruct
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_EnumAttr
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_GlobalConstantDefinition
import net.postchain.rell.base.model.R_KeyIndexKind
import net.postchain.rell.base.model.R_Module
import net.postchain.rell.base.model.R_ObjectDefinition
import net.postchain.rell.base.model.R_StructDefinition
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DEnumEntry
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.IsVar
import org.jetbrains.dokka.model.KotlinModifier
import org.jetbrains.dokka.model.KotlinVisibility
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.utilities.DokkaLogger

internal class RellModuleVisitor(
        private val sourceSet: DokkaConfiguration.DokkaSourceSet,
        private val logger: DokkaLogger,
) {

    fun visitRellModule(module: R_Module): DPackage {
        val dri = DRI(packageName = module.name.str())

        val globalConstants = module.constants.values.map { it.visit() }
        val entities = module.entities.values.map { it.visit() }
        val structs = module.structs.values.map { it.visit() }
        val objects = module.objects.values.map { it.visit() }
        val enums = module.enums.values.map { it.visit() }

        return DPackage(
                dri = dri,
                properties = globalConstants,
                classlikes = entities + structs + objects + enums,
                functions = listOf(),
                typealiases = listOf(),
                documentation = module.docSymbol.toDocumentationNode().toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
        )
    }

    private fun R_GlobalConstantDefinition.visit(): DProperty {
        return DProperty(
                dri = DRI.from(this),
                name = simpleName,
                receiver = null,
                setter = null,
                getter = null,
                visibility = KotlinVisibility.Public.toSourceSetDependent(),
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                generics = listOf(),
                isExpectActual = false,
                sourceSets = setOf(sourceSet),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
                type = type().toBound(),
                expectPresentInSet = null,
                documentation = simpleDocumentationNode("This is constant $simpleName").toSourceSetDependent()
        )
    }

    private fun R_EntityDefinition.visit(): DClass {

        val dri = DRI.from(this)
        val properties = this.attributes.values.map { it.visit(dri) }

        return DClass(
                dri = dri,
                name = simpleName,
                properties = properties,
                documentation = simpleDocumentationNode("This entity is called $simpleName").toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
                classlikes = listOf(),
                companion = null,
                constructors = listOf(),
                expectPresentInSet = null,
                functions = listOf(),
                generics = listOf(),
                isExpectActual = false,
                visibility = KotlinVisibility.Public.toSourceSetDependent(),
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                supertypes = mapOf(),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
                extra = PropertyContainer.withAll(IsEntity)
        )
    }

    private fun R_StructDefinition.visit(): DClass {

        val dri = DRI.from(this)
        val properties = this.struct.attributes.values.map { it.visit(dri) }

        return DClass(
                dri = dri,
                name = simpleName,
                properties = properties,
                documentation = simpleDocumentationNode("This struct is called $simpleName").toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
                classlikes = listOf(),
                companion = null,
                constructors = listOf(),
                expectPresentInSet = null,
                functions = listOf(),
                generics = listOf(),
                isExpectActual = false,
                visibility = KotlinVisibility.Public.toSourceSetDependent(),
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                supertypes = mapOf(),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
                extra = PropertyContainer.withAll(IsStruct)
        )
    }

    private fun R_ObjectDefinition.visit(): DClass {

        val dri = DRI.from(this)
        val properties = this.rEntity.attributes.values.map { it.visit(dri) }

        return DClass(
                dri = dri,
                name = simpleName,
                properties = properties,
                documentation = simpleDocumentationNode("This object is called $simpleName").toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
                classlikes = listOf(),
                companion = null,
                constructors = listOf(),
                expectPresentInSet = null,
                functions = listOf(),
                generics = listOf(),
                isExpectActual = false,
                visibility = KotlinVisibility.Public.toSourceSetDependent(),
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                supertypes = mapOf(),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
                extra = PropertyContainer.withAll(IsObject)
        )
    }

    private fun R_Attribute.visit(parent: DRI): DProperty {
        return DProperty(
                dri = parent.withClass(name),
                name = name,
                type = type.toBound(),
                documentation = simpleDocumentationNode("This propertiy is called $name").toSourceSetDependent(),
                visibility = KotlinVisibility.Public.toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                isExpectActual = false,
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
                expectPresentInSet = null,
                getter = null,
                setter = null,
                receiver = null,
                extra = PropertyContainer.withAll(
                        when (keyIndexKind) {
                            R_KeyIndexKind.KEY -> IsKey
                            R_KeyIndexKind.INDEX -> IsIndex
                            else -> null
                        },
                        IsVar.takeIf { mutable }
                )
        )
    }

    private fun R_EnumDefinition.visit(): DEnum {
        val dri = DRI.from(this)
        val entries = this.attrs.map { it.visit(dri) }

        return DEnum(
                dri = dri,
                name = simpleName,
                entries = entries,
                documentation = simpleDocumentationNode("This enum is called $simpleName").toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
                properties = listOf(),
                classlikes = listOf(),
                companion = null,
                constructors = listOf(),
                expectPresentInSet = null,
                functions = listOf(),
                isExpectActual = false,
                visibility = KotlinVisibility.Public.toSourceSetDependent(),
                supertypes = mapOf(),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
        )
    }

    private fun R_EnumAttr.visit(parent: DRI): DEnumEntry {
        return DEnumEntry(
                dri = parent.withClass(name),
                name = name,
                documentation = simpleDocumentationNode("This enum entry is called $name").toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
                expectPresentInSet = null,
                classlikes = listOf(),
                functions = listOf(),
                properties = listOf()
        )
    }

    private fun <T> T.toSourceSetDependent() = if (this != null) mapOf(sourceSet to this) else mapOf()
}



