package com.chromia.rell.dokka.translators

import com.chromia.rell.dokka.analysis.RellAnalysis
import com.chromia.rell.dokka.doc.RellDocumentableSource
import com.chromia.rell.dokka.doc.simpleDocumentationNode
import com.chromia.rell.dokka.doc.toDocumentationNode
import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.dri.toBound
import com.chromia.rell.dokka.model.ExtensionFunction
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
import com.chromia.rell.dokka.model.toExpression
import com.chromia.rell.dokka.reflection.getParamsByReflection
import com.chromia.rell.dokka.reflection.getTypeByReflection
import com.chromia.rell.dokka.reflection.getValueByReflection
import com.chromia.rell.dokka.reflection.getSubExprByReflection
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_EnumAttr
import net.postchain.rell.base.model.R_EnumDefinition
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
import net.postchain.rell.base.model.expr.R_ConstantValueExpr
import net.postchain.rell.base.model.expr.R_StackTraceExpr
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.ComplexExpression
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DEnumEntry
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.DefaultValue
import org.jetbrains.dokka.model.IsVar
import org.jetbrains.dokka.model.KotlinModifier
import org.jetbrains.dokka.model.KotlinVisibility
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.ExtraProperty
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.utilities.DokkaLogger

internal class RellModuleVisitor(
        private val sourceSet: DokkaConfiguration.DokkaSourceSet,
        private val logger: DokkaLogger,
        private val rellAnalysis: RellAnalysis
) {

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

        val extensionFunctions = rellAnalysis.getExtensionFunctions(module.name.str()).mapNotNull { it.visit() }

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
                sources = RellDocumentableSource.NULL.toSourceSetDependent(),
                type = getTypeByReflection().toBound(),
                expectPresentInSet = null,
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
                extra = PropertyContainer.withAll(
                        DefaultValue(ComplexExpression(toMetaGtv()["value"].toString()).toSourceSetDependent())
                )
        )
    }

    private fun R_EntityDefinition.visit(): DClass {

        val dri = DRI.from(this)
        val properties = this.attributes.values.map { it.visit(dri) }

        return DClass(
                dri = dri,
                name = simpleName,
                properties = properties,
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
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
                sources = RellDocumentableSource.NULL.toSourceSetDependent(),
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
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
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
                sources = RellDocumentableSource.NULL.toSourceSetDependent(),
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
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
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
                sources = RellDocumentableSource.NULL.toSourceSetDependent(),
                extra = PropertyContainer.withAll(IsObject)
        )
    }

    private fun R_Attribute.visit(parent: DRI): DProperty {
        return DProperty(
                dri = parent.withClass(name),
                name = name,
                type = type.toBound(),
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
                visibility = KotlinVisibility.Public.toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                isExpectActual = false,
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                sources = RellDocumentableSource.NULL.toSourceSetDependent(),
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
                        IsVar.takeIf { mutable },
                        expr?.let { it as? R_StackTraceExpr }?.getSubExprByReflection()
                                ?.let { it as? R_ConstantValueExpr }?.getValueByReflection()
                                ?.let { DefaultValue(it.toExpression().toSourceSetDependent()) }
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
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
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
                sources = RellDocumentableSource.NULL.toSourceSetDependent(),
        )
    }

    private fun R_EnumAttr.visit(parent: DRI): DEnumEntry {
        return DEnumEntry(
                dri = parent.withClass(name),
                name = name,
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
                sourceSets = setOf(sourceSet),
                expectPresentInSet = null,
                classlikes = listOf(),
                functions = listOf(),
                properties = listOf()
        )
    }

    private fun R_FunctionDefinition.visit(): DFunction? {
        val isExtendable = rellAnalysis.isExtendable(this.appLevelName)
        if (!isExtendable && rellAnalysis.hasExtension(this)) {
            return null
        }
        return visit(IsFunction, IsExtendable.takeIf { isExtendable })
    }

    private fun R_OperationDefinition.visit() = visit(IsOperation)
    private fun R_QueryDefinition.visit() = visit(IsQuery)

    private fun R_RoutineDefinition.visit(vararg extraProperty: ExtraProperty<DFunction>?): DFunction {
        val dri = DRI.from(this)
        val params = (if (this is R_FunctionDefinition) getParamsByReflection() else params()) // Temporary hack due to bug in rell
                .mapIndexed { index, param -> param.visit(dri, index) }
        return DFunction(
                dri = dri,
                name = simpleName,
                isConstructor = false,
                parameters = params,
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
                expectPresentInSet = null,
                visibility = mapOf(),
                receiver = null,
                isExpectActual = false,
                type = getTypeByReflection().toBound(),
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                sources = RellDocumentableSource.NULL.toSourceSetDependent(),
                modifier = KotlinModifier.Empty.toSourceSetDependent(),
                extra = PropertyContainer.withAll(listOfNotNull(*extraProperty))
        )
    }

    private fun ExtensionFunction.visit(): DFunction? {
        val dri = DRI(
                packageName = defName.module,
                callable = Callable.from(
                        defName.simpleName,
                        fnBase.getHeader().params
                )
        )
        val targetDri = rellAnalysis.findFunctionReference(targetAppLevelName) ?: return null
        if (dri == targetDri) return null
        val params = fnBase.getHeader().params.mapIndexed { index, param -> param.visit(dri, index) }
        return DFunction(
                dri = dri,
                name = defName.simpleName,
                isConstructor = false,
                parameters = params,
                documentation = simpleDocumentationNode("").toSourceSetDependent(),
                expectPresentInSet = null,
                visibility = mapOf(),
                receiver = null,
                isExpectActual = false,
                type = fnBase.getHeader().type.toBound(),
                sourceSets = setOf(sourceSet),
                generics = listOf(),
                sources = RellDocumentableSource.NULL.toSourceSetDependent(),
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
            documentation = mapOf(sourceSet to DocumentationNode(listOf(Description(Text(""))))),
    )

    private fun <T> T.toSourceSetDependent() = if (this != null) mapOf(sourceSet to this) else mapOf()
}



