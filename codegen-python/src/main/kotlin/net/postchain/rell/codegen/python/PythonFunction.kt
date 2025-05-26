package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.DocumentSection

abstract class PythonFunction(
        protected val className: ClassName,
        protected val mountName: R_MountName,
        protected val params: List<R_FunctionParam>,
        override val docSymbol: DocSymbol,
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

    final override fun format(): String = /*TODO: */ "// <Function>"

    fun imports(impl: PyFunctionImplementations): List<String> = /*TODO: */ emptyList()

    abstract fun formatReturnObject(): String
    abstract fun formatReturnType(): String
}

enum class PyFunctionImplementations {
    QUERY, OPERATION
}