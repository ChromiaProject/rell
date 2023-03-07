package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.typescript.util.rTypeToString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.R_MountName
import net.postchain.rell.model.R_Param
import net.postchain.rell.model.R_Type

abstract class TypescriptFunction(
        protected val className: ClassName,
        protected val mountName: R_MountName,
        protected val params: List<R_Param>,
        private val async: Boolean,
        protected val returnType: R_Type?,

        ) : DocumentSection {
    override val moduleName get() = className.module

    final override val deps: Set<ClassName>

    init {
        val returnDeps = DependencyFinder.findDependencies(returnType)
        val paramDeps = DependencyFinder.findDependencies(params.map { it.type })
        deps = paramDeps + returnDeps
    }

    override fun format() = """
        |${returnStructure(returnType)}
        |export ${asyncAnnotation()}function ${className.name.snakeToLowerCamelCase()}(${formatInputParameters()}): ${formatReturnType()} {
        |${"\t"}${formatBody()}
        |}
   """.trimMargin()

    private fun asyncAnnotation() = if (async) "async " else ""

    open fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t") { "${it.name.str.snakeToLowerCamelCase()}: ${rTypeToString(it.type)}" }
    }

    abstract fun returnStructure(returnType: R_Type?): String
    abstract fun formatReturnType(): String
    abstract fun formatBody(): String
}
