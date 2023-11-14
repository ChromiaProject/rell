package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.typescript.util.rTypeToString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

abstract class TypescriptFunction(
        protected val className: ClassName,
        protected val mountName: R_MountName,
        protected val params: List<R_FunctionParam>,
        private val async: Boolean,
        protected val returnType: R_Type?,
        private val querySuffix: String = "",
        ) : DocumentSection {
    override val moduleName get() = className.module

    final override val deps: Set<ClassName>

    init {
        val returnDeps = DependencyFinder.findDependencies(returnType)
        val paramDeps = DependencyFinder.findDependencies(params.map { it.type })
        deps = paramDeps + returnDeps
    }

    final override fun format(): String {
        val returnTypeString = "${returnStructure(returnType)}\n"
        val functionString = """
        |export ${asyncAnnotation()}function ${className.className.snakeToLowerCamelCase()}$querySuffix(${formatInputParameters()}): ${formatReturnType()} {
        |${"\t"}${formatBody()}
        |}
   """.trimMargin()
        return StringBuilder()
                .append(returnTypeString.ifBlank { "" })
                .append(functionString)
                .toString()
    }

    private fun asyncAnnotation() = if (async) "async " else ""

    private fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t") { "${it.name.str.snakeToLowerCamelCase()}: ${rTypeToString(it.type, true)}" }
    }

    abstract fun returnStructure(returnType: R_Type?): String
    abstract fun formatReturnType(): String
    abstract fun formatBody(): String
}
