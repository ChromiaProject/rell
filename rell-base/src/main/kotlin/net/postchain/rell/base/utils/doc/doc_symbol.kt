/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.immMapOf

enum class DocSymbolKind(val msg: String) {
    NONE("symbol"),
    MODULE("module"),
    NAMESPACE("namespace"),
    CONSTANT("constant"),
    PROPERTY("property"),
    TYPE("type"),
    TYPE_EXTENSION("type extension"),
    ENUM("enum"),
    ENUM_VALUE("enum value"),
    ENTITY("entity"),
    ENTITY_ATTR("entity attribute"),
    OBJECT("object"),
    OBJECT_ATTR("object attribute"),
    STRUCT("struct"),
    STRUCT_ATTR("struct attribute"),
    CONSTRUCTOR("constructor"),
    FUNCTION("function"),
    OPERATION("operation"),
    QUERY("query"),
    ALIAS("alias"),
    PARAMETER("parameter"),
    IMPORT("import"),
    TUPLE_ATTR("tuple attribute"),
    AT_VAR_COL("collection-at variable"),
    AT_VAR_DB("database-at variable"),
    VAR("variable"),
}

class DocSymbol(
    val kind: DocSymbolKind,
    val symbolName: DocSymbolName,
    val mountName: String?,
    val declaration: DocDeclaration,
    val comment: DocComment?,
) {
    override fun toString() = "$symbolName | $declaration"

    companion object {
        val NONE = DocSymbol(
            kind = DocSymbolKind.NONE,
            symbolName = DocSymbolName.module(R_ModuleName.EMPTY),
            mountName = null,
            declaration = DocDeclaration.NONE,
            comment = null,
        )
    }
}

sealed class DocSymbolName {
    abstract fun strCode(): String
    final override fun toString() = strCode()

    companion object {
        fun module(moduleName: String): DocSymbolName {
            return DocSymbolName_Module(moduleName)
        }

        fun module(moduleName: R_ModuleName): DocSymbolName {
            return module(moduleName.str())
        }

        fun global(moduleName: String, qualifiedName: String): DocSymbolName {
            return DocSymbolName_Global(moduleName, qualifiedName)
        }

        fun global(fullName: R_FullName): DocSymbolName {
            return global(fullName.moduleName.str(), fullName.qualifiedName.str())
        }

        fun local(simpleName: String): DocSymbolName {
            check(simpleName.isNotBlank())
            return DocSymbolName_Local(simpleName)
        }
    }
}

private class DocSymbolName_Module(
    private val moduleName: String,
): DocSymbolName() {
    override fun strCode() = moduleName
}

private class DocSymbolName_Global(
    private val moduleName: String,
    private val qualifiedName: String,
): DocSymbolName() {
    init {
        require(qualifiedName.isNotEmpty())
    }

    override fun strCode() = "$moduleName:$qualifiedName"
}

private class DocSymbolName_Local(
    private val simpleName: String,
): DocSymbolName() {
    override fun strCode() = simpleName
}

data class DocSourcePos(
    val path: String,
    val line: Int,
) {
    fun str() = "$path:$line"
    override fun toString() = str()
}

abstract class DocDefinition {
    abstract val docSymbol: DocSymbol
    abstract val docSourcePos: DocSourcePos?

    val docMembers: Map<String, DocDefinition> by lazy {
        getDocMembers0()
    }

    protected open fun getDocMembers0(): Map<String, DocDefinition> = immMapOf()

    fun getDocMember(name: String): DocDefinition? {
        return docMembers[name]
    }
}
