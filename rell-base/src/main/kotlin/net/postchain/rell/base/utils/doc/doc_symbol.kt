/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.ide.IdeCompletionParam
import net.postchain.rell.base.utils.immMapOf

enum class DocSymbolKind constructor(
    val msg: String,
    val supportedTags: SupportedCommentTags = tags(),
) {
    NONE("symbol"),
    MODULE("module", tags(author = true)),
    NAMESPACE("namespace", tags(author = true)),
    CONSTANT("constant", tags(author = true)),
    PROPERTY("property", tags(author = true, throws = true)),
    TYPE("type", tags(author = true)),
    TYPE_EXTENSION("type extension", tags(author = true)),
    ENUM("enum", tags(author = true)),
    ENUM_VALUE("enum value"),
    ENTITY("entity", tags(author = true)),
    ENTITY_ATTR("entity attribute"),
    OBJECT("object", tags(author = true)),
    OBJECT_ATTR("object attribute"),
    STRUCT("struct", tags(author = true)),
    STRUCT_ATTR("struct attribute"),
    CONSTRUCTOR("constructor", tags(param = true, throws = true)),
    FUNCTION("function", tags(author = true, param = true, returns = true, throws = true)),
    OPERATION("operation", tags(author = true, param = true, throws = true)),
    QUERY("query", tags(author = true, param = true, returns = true, throws = true)),
    ALIAS("alias"),
    PARAMETER("parameter"),
    IMPORT("import"),
    TUPLE_ATTR("tuple attribute"),
    AT_VAR_COL("collection-at variable"),
    AT_VAR_DB("database-at variable"),
    VAR("variable"),
    ;

    class SupportedCommentTags(
        val author: Boolean,
        val param: Boolean,
        val returns: Boolean,
        val throws: Boolean,
    )
}

private fun tags(
    author: Boolean = false,
    param: Boolean = false,
    returns: Boolean = false,
    throws: Boolean = false,
) = DocSymbolKind.SupportedCommentTags(
    author = author,
    param = param,
    returns = returns,
    throws = throws,
)

class DocDeclaration(
    val code: DocCode,
    internal val internalCompletion: Completion?,
    internal val isDeprecated: Boolean,
) {
    @Suppress("unused")
    @Deprecated("intended for internal use; but kept public for compatibility with client code")
    val completion = internalCompletion

    class Completion(
        val params: ImmList<IdeCompletionParam>?,
        val result: String?,
    )

    companion object {
        val NONE: DocDeclaration = DocDeclaration(DocCode.EMPTY, null, false)
    }
}

class DocSymbol(
    val kind: DocSymbolKind,
    val symbolName: DocSymbolName,
    val mountName: String?,
    val comment: DocComment?,
    declaration: Lazy<DocDeclaration>,
) {
    private val lazyDeclaration = declaration

    val declaration: DocDeclaration get() = lazyDeclaration.value

    override fun toString() = "$symbolName|$kind"

    companion object {
        val NONE = DocSymbol(
            kind = DocSymbolKind.NONE,
            symbolName = DocSymbolName.module(R_ModuleName.EMPTY),
            mountName = null,
            comment = null,
            declaration = lazyOf(DocDeclaration.NONE),
        )
    }
}

sealed class DocSymbolName {
    abstract fun strCode(): String
    final override fun toString() = strCode()

    open fun parentName(): DocSymbolName? = null

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

private data class DocSymbolName_Module(
    private val moduleName: String,
): DocSymbolName() {
    override fun strCode() = moduleName
}

private data class DocSymbolName_Global(
    private val moduleName: String,
    private val qualifiedName: String,
): DocSymbolName() {
    init {
        require(qualifiedName.isNotEmpty())
    }

    override fun strCode() = "$moduleName:$qualifiedName"

    override fun parentName(): DocSymbolName? {
        val i = qualifiedName.lastIndexOf('.')
        return if (i < 0) null else DocSymbolName_Global(moduleName, qualifiedName.substring(0, i))
    }
}

private data class DocSymbolName_Local(
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

    val docMembers: ImmMap<String, DocDefinition> by lazy {
        getDocMembers0()
    }

    protected open fun getDocMembers0(): ImmMap<String, DocDefinition> = immMapOf()

    fun getDocMember(name: String): DocDefinition? {
        return docMembers[name]
    }
}
