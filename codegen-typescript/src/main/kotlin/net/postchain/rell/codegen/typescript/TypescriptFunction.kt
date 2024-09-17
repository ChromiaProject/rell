package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.rTypeToJsTypeString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

abstract class TypescriptFunction(
        protected val className: ClassName,
        protected val mountName: R_MountName,
        protected val params: List<R_FunctionParam>,
        override val docSymbol: DocSymbol,
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
        |${TypescriptDocGenerator.formatDoc(docSymbol, wrapInDocComments = true, params, formatReturnType())}
        |export ${asyncAnnotation()}function ${className.className.snakeToLowerCamelCase()}$querySuffix(${formatInputParameters()}): ${formatReturnType()} {
        |${"\t"}${formatReturnObject()}
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
        return params.joinToString(",\n\t") { "${it.name.str.snakeToLowerCamelCase()}: ${rTypeToJsTypeString(it.type, true)}" }
    }

    private fun formatReturnObject(): String = buildString {
        append("return { name: \"$mountName\"")
        if (params.isNotEmpty()) {
            append(", args: ${formatReturnObjectArgs()}")
        }
        append(" };")
    }

    abstract fun formatReturnObjectArgs(): String
    abstract fun returnStructure(returnType: R_Type?): String
    abstract fun formatReturnType(): String

}
