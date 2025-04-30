/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.ide

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap
import java.util.*

enum class IdeSymbolKind {
    UNKNOWN,
    DEF_CONSTANT,
    DEF_ENTITY,
    DEF_ENUM,
    DEF_FUNCTION,
    DEF_FUNCTION_ABSTRACT,
    DEF_FUNCTION_EXTEND,
    DEF_FUNCTION_EXTENDABLE,
    DEF_FUNCTION_SYSTEM,
    DEF_IMPORT_ALIAS,
    DEF_IMPORT_MODULE,
    DEF_NAMESPACE,
    DEF_OBJECT,
    DEF_OPERATION,
    DEF_QUERY,
    DEF_STRUCT,
    DEF_TYPE,
    MEM_ENTITY_ATTR_INDEX,
    MEM_ENTITY_ATTR_INDEX_VAR,
    MEM_ENTITY_ATTR_KEY,
    MEM_ENTITY_ATTR_KEY_VAR,
    MEM_ENTITY_ATTR_NORMAL,
    MEM_ENTITY_ATTR_NORMAL_VAR,
    MEM_ENTITY_ATTR_ROWID,
    MEM_ENUM_VALUE,
    MEM_STRUCT_ATTR,
    MEM_STRUCT_ATTR_VAR,
    MEM_SYS_PROPERTY,
    MEM_SYS_PROPERTY_PURE,
    MEM_TUPLE_ATTR,
    MOD_ANNOTATION,
    MOD_ANNOTATION_LEGACY,
    LOC_AT_ALIAS,
    LOC_PARAMETER,
    LOC_VAL,
    LOC_VAR,
    EXPR_CALL_ARG,
    EXPR_IMPORT_ALIAS,
}

enum class IdeSymbolCategory(@JvmField val code: String) {
    ATTRIBUTE("attr"),
    CONSTANT("constant"),
    ENTITY("entity"),
    ENUM("enum"),
    ENUM_VALUE("value"),
    FUNCTION("function"),
    IMPORT("import"),
    NAMESPACE("namespace"),
    OBJECT("object"),
    OPERATION("operation"),
    PARAMETER("param"),
    QUERY("query"),
    STRUCT("struct"),
    TUPLE("tuple"),
}

class IdeSymbolId(
    private val category: IdeSymbolCategory,
    private val name: String,
    private val members: ImmList<Pair<IdeSymbolCategory, R_Name>> = immListOf(),
) {
    fun encode(): String {
        val path = listOf(category to name) + members.map { it.first to it.second.str }
        return path.joinToString(".") { "${it.first.code}[${it.second}]" }
    }

    fun appendMember(memberCategory: IdeSymbolCategory, memberName: R_Name): IdeSymbolId {
        val members2 = (members + listOf(memberCategory to memberName)).toImmList()
        return IdeSymbolId(category, name, members2)
    }

    override fun equals(other: Any?) = other is IdeSymbolId && category == other.category && name == other.name && members == other.members
    override fun hashCode() = Objects.hash(category, name, members)
    override fun toString() = encode()
}

class IdeSymbolGlobalId(@JvmField val file: IdeFilePath, @JvmField val symId: IdeSymbolId) {
    fun encode(): String = "$file/${symId.encode()}"
    override fun toString() = encode()
}

sealed class IdeSymbolLink {
    abstract fun encode(): String
    final override fun toString(): String = encode()

    open fun moduleFile(): IdeFilePath? = null
    open fun localPos(): S_Pos? = null
    open fun globalId(): IdeSymbolGlobalId? = null
}

class IdeModuleSymbolLink(private val file: IdeFilePath): IdeSymbolLink() {
    override fun encode() = file.toString()
    override fun moduleFile() = file
}

class IdeLocalSymbolLink(private val pos: S_Pos): IdeSymbolLink() {
    override fun encode() = "local[$pos]"
    override fun localPos() = pos
}

class IdeGlobalSymbolLink(private val globalId: IdeSymbolGlobalId): IdeSymbolLink() {
    override fun encode() = globalId.encode()
    override fun globalId() = globalId
}

class IdeSymbolInfo private constructor(
    @JvmField val kind: IdeSymbolKind,
    @JvmField val defId: IdeSymbolId?,
    @JvmField val link: IdeSymbolLink?,
    @JvmField val doc: DocSymbol?,
) {
    override fun toString() = kind.name

    fun update(
        kind: IdeSymbolKind = this.kind,
        defId: IdeSymbolId? = this.defId,
        link: IdeSymbolLink? = this.link,
        doc: DocSymbol? = this.doc,
    ): IdeSymbolInfo {
        return if (kind == this.kind && defId === this.defId && link === this.link && doc === this.doc) this else {
            IdeSymbolInfo(kind = kind, defId = defId, link = link, doc = doc)
        }
    }

    companion object {
        private val byKind: Map<IdeSymbolKind, IdeSymbolInfo> = IdeSymbolKind.values()
            .map { it to IdeSymbolInfo(it, defId = null, link = null, doc = null) }
            .toImmMap()

        fun get(kind: IdeSymbolKind): IdeSymbolInfo = byKind.getValue(kind)

        fun make(
            kind: IdeSymbolKind,
            defId: IdeSymbolId?,
            link: IdeSymbolLink?,
            doc: DocSymbol?,
        ): IdeSymbolInfo {
            return if (defId == null && link == null && doc == null) {
                get(kind)
            } else {
                IdeSymbolInfo(kind = kind, defId = defId, link = link, doc = doc)
            }
        }
    }
}
