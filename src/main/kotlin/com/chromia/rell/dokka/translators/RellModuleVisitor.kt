package com.chromia.rell.dokka.translators

import com.chromia.rell.dokka.descriptors.NULL_DESCRIPTOR
import com.chromia.rell.dokka.doc.simpleDocumentationNode
import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.model.ExtensionFunctionExtra
import com.chromia.rell.dokka.model.IsAnonymous
import com.chromia.rell.dokka.model.IsEntity
import com.chromia.rell.dokka.model.IsExtendable
import com.chromia.rell.dokka.model.IsFunction
import com.chromia.rell.dokka.model.IsIndex
import com.chromia.rell.dokka.model.IsKey
import com.chromia.rell.dokka.model.IsObject
import com.chromia.rell.dokka.model.IsOperation
import com.chromia.rell.dokka.model.IsQuery
import com.chromia.rell.dokka.model.IsStruct
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_EnumAttr
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_FunctionBase
import net.postchain.rell.base.model.R_FunctionDefinition
import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.model.R_GlobalConstantDefinition
import net.postchain.rell.base.model.R_KeyIndexKind
import net.postchain.rell.base.model.R_Module
import net.postchain.rell.base.model.R_ObjectDefinition
import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_RoutineDefinition
import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.base.model.expr.R_FunctionExtension
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DEnumEntry
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.IsVar
import org.jetbrains.dokka.model.KotlinModifier
import org.jetbrains.dokka.model.KotlinVisibility
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.ExtraProperty
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.utilities.DokkaLogger
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

internal class RellModuleVisitor(
        private val sourceSet: DokkaConfiguration.DokkaSourceSet,
        private val logger: DokkaLogger,
        app: R_App,
        private val functionExtensions:  Map<String, List<R_FunctionExtension>>
) {

    val allFunctions = app.modules.flatMap { it.functions.values }.associateBy { it.defName.appLevelName }

    val extensionFunctionsByModule = functionExtensions
            .flatMap { (target, list) -> list.map { f -> target to f } }
            .map { (target, f) ->
        R_FunctionBase::class.memberProperties.find { it.name == "defName" }!!.let {
            it.isAccessible = true
            ExtensionFunction(target, (it.get(f.fnBase) as R_DefinitionName), f)
        }
    }.groupBy {  it.defName.module }

    internal class ExtensionFunction(val target: String, val defName: R_DefinitionName, val f: R_FunctionExtension)

    fun visitRellModule(module: R_Module): DPackage {
        val dri = DRI(packageName = module.name.str())

        val globalConstants = module.constants.values.map { it.visit() }
        val entities = module.entities.values.map { it.visit() }
        val structs = module.structs.values.map { it.visit() }
        val objects = module.objects.values.map { it.visit() }
        val enums = module.enums.values.map { it.visit() }
        val functions = module.functions.values.mapNotNull { it.visit() }
        val operations = module.operations.values.map { it.visit() }
        val queries = module.queries.values.map { it.visit() }

        val extensionFunctions = extensionFunctionsByModule[module.name.str()]?.mapNotNull { it.visit() } ?: listOf()

        logger.info("Module: ${module.name}")
        logger.info("Found ${globalConstants.size} constants, ${entities.size} entities, ${structs.size} structs, ${objects.size} objects, ${enums.size} enums, " +
                "${functions.size} functions, ${operations.size} operations and ${queries.size} queries")

        return DPackage(
                dri = dri,
                properties = globalConstants,
                classlikes = entities + structs + objects + enums,
                functions = functions + operations + queries + extensionFunctions,
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

    private fun R_FunctionDefinition.visit(): DFunction? {
        val isExtendable = functionExtensions.containsKey(appLevelName)
        // TODO: Do not flatten on each function visit..
        if (!isExtendable && extensionFunctionsByModule.values.flatten().find { it.defName.appLevelName == this.appLevelName } != null) {
            return null
        }
        return visit(IsFunction, IsExtendable.takeIf { isExtendable })
    }
    private fun R_OperationDefinition.visit() = visit(IsOperation)
    private fun R_QueryDefinition.visit() = visit(IsQuery)

    private fun R_RoutineDefinition.visit(vararg extraProperty: ExtraProperty<DFunction>?): DFunction {
        val dri = DRI.from(this)
        val params = params().mapIndexed { index, param  -> param.visit(dri, index) }
        return  DFunction(
                dri = dri,
                name = simpleName,
                isConstructor = false,
                parameters = params,
                documentation = simpleDocumentationNode("This $extraProperty is called $simpleName").toSourceSetDependent(),
                expectPresentInSet = null,
                visibility = mapOf(),
                receiver = null,
                isExpectActual = false,
                type = type().toBound(),
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                extra = PropertyContainer.withAll(listOfNotNull(*extraProperty))
        )
    }

    private fun ExtensionFunction.visit(): DFunction? {
        val dri = DRI(
                packageName = defName.module,
                callable = Callable.from(
                        defName.simpleName,
                        f.fnBase.getHeader().params
                )
        )
        val targetDri = allFunctions[target]?.visit()?.dri ?: return null
        if (dri == targetDri) return null
        val params = f.fnBase.getHeader().params.mapIndexed { index, param  -> param.visit(dri, index) }
        return  DFunction(
                dri = dri,
                name = defName.simpleName,
                isConstructor = false,
                parameters = params,
                documentation = simpleDocumentationNode("This extension function is called ${defName.simpleName}").toSourceSetDependent(),
                expectPresentInSet = null,
                visibility = mapOf(),
                receiver = null,
                isExpectActual = false,
                type = f.fnBase.getHeader().type.toBound(),
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                sources = NULL_DESCRIPTOR.toSourceSetDependent(),
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                extra = PropertyContainer.withAll(
                        IsFunction,
                        ExtensionFunctionExtra(targetDri),
                        IsAnonymous.takeIf { defName.simpleName.startsWith("function#") }
                )
        )
    }

    private fun R_FunctionParam.visit(parent: DRI, paramIndex: Int) = DParameter(
            dri = parent.copy(target = PointingToCallableParameters(paramIndex)),
            name = name.str,
            expectPresentInSet = null,
            type = type.toBound(),
            sourceSets = setOf(sourceSet),
            documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text("This parameter is called $name"))))),
    )

    private fun <T> T.toSourceSetDependent() = if (this != null) mapOf(sourceSet to this) else mapOf()
}



