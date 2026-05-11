/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.MountName
import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.camelToSnakeCase
import net.postchain.rell.codegen.util.rTypeToPythonType

abstract class PythonFunction(
        protected val className: ClassName,
        protected val mountName: MountName,
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

    final override fun format(): String {
        val functionString = """
        |${PythonDocGenerator.formatDoc(docSymbol, wrapInDocComments = true, params, formatReturnType())}
        |def ${sanitizedFunName()}$querySuffix(${formatInputParameters()}) -> ${formatReturnType()}:
        |${"\t"}${formatReturnObject()}
        """.trimMargin()
        return StringBuilder()
                .append(functionString)
                .toString()
    }

    fun sanitizedFunName(): String = mountName.str()
            .replace('.', '_')

    fun imports(impl: PyFunctionImplementations): List<String> = buildList {
        add("from dataclasses import dataclass")
        add("from typing import Dict, Any, List, Set, Optional")
        add("from enum import Enum")
        add("from postchain_client_py.utils.gtv import RawGtv")
        add("from postchain_client_py.utils.types import BigInt")
        when(impl) {
            PyFunctionImplementations.QUERY -> add(
                "from postchain_client_py.blockchain_client.types import QueryObject"
            )
            PyFunctionImplementations.OPERATION -> add(
                "from postchain_client_py.blockchain_client.types import Operation"
            )
        }
    }

    private fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(", ") { "${it.name.str.camelToSnakeCase()}: ${rTypeToPythonType(it.type)}" }
    }

    abstract fun formatReturnObject(): String
    abstract fun formatReturnObjectArgs(): String
    abstract fun formatReturnType(): String
}

enum class PyFunctionImplementations {
    QUERY, OPERATION
}