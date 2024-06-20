/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.namespace

import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfoHandle
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.core.C_QualifiedName
import net.postchain.rell.base.compiler.base.core.C_UniqueDefaultIdeInfoPtr
import net.postchain.rell.base.compiler.base.expr.C_Expr
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDefinition

class C_Deprecated(
    private val useInstead: String?,
    val error: Boolean = false
) {
    fun detailsCode(): String {
        return if (useInstead != null) ":$useInstead" else ""
    }

    fun detailsMessage(): String {
        return if (useInstead != null) ", use '$useInstead' instead" else ""
    }

    companion object {
        fun make(error: Boolean = false, useInstead: String? = null): C_Deprecated {
            return C_Deprecated(useInstead = useInstead, error = error)
        }

        fun makeOrNull(messageType: C_MessageType?, useInstead: String?): C_Deprecated? {
            return if (messageType == null) null else {
                C_Deprecated(useInstead = useInstead, error = messageType == C_MessageType.ERROR)
            }
        }

        fun warning(useInstead: String? = null): C_Deprecated {
            return make(error = false, useInstead = useInstead)
        }

        fun error(useInstead: String? = null): C_Deprecated {
            return make(error = true, useInstead = useInstead)
        }
    }
}

enum class C_DeclarationType(val msg: String, val article: String = "a") {
    MODULE("module"),
    NAMESPACE("namespace"),
    ALIAS("alias", "an"),
    TYPE("type"),
    ENTITY("entity", "an"),
    STRUCT("struct"),
    ENUM("enum", "an"),
    OBJECT("object", "an"),
    FUNCTION("function"),
    OPERATION("operation", "an"),
    QUERY("query"),
    IMPORT("import", "an"),
    CONSTANT("constant"),
    PROPERTY("property"),
    CONSTRUCTOR("constructor"),
    ATTRIBUTE("attribute", "an"),
    PARAMETER("parameter"),
    ANNOTATION("annotation"),
    ;

    val capitalizedMsg = msg.capitalizeEx()
}

class C_NamespaceElement(
    val member: C_NamespaceMember,
    allMembers: List<C_NamespaceMember>,
) {
    private val allMembers = allMembers.toImmList()

    fun access(msgCtx: C_MessageContext, lazyName: LazyPosString) {
        if (allMembers.size > 1) {
            val nameStr = lazyName.str
            val listCodeMsg = allMembers.map {
                val declType = it.declarationType()
                val defName = it.defName.appLevelName
                "$declType:[$defName]" toCodeMsg "${declType.msg} '$defName'"
            }
            val listCode = listCodeMsg.joinToString(",") { it.code }
            val listMsg = listCodeMsg.joinToString { it.msg }
            msgCtx.error(lazyName.pos, "namespace:ambig:$nameStr:[$listCode]", "Name '$nameStr' is ambiguous: $listMsg")
        }

        member.restrictions.access(msgCtx, lazyName.pos)
    }

    fun toExpr(ctx: C_ExprContext, qName: C_QualifiedName, ideInfoHand: C_IdeSymbolInfoHandle): C_Expr {
        access(ctx.msgCtx, qName.toLazyPosString())

        val ideInfoPtr = C_UniqueDefaultIdeInfoPtr(ideInfoHand, member.ideInfo)
        val expr = member.toExpr(ctx, qName, ideInfoPtr)

        if (ideInfoPtr.isValid()) {
            ideInfoPtr.setDefault()
        }

        return expr
    }
}

class C_NamespaceEntry(
    directMembers: List<C_NamespaceMember>,
    importMembers: List<C_NamespaceMember>,
) {
    val directMembers = directMembers.toImmList()
    val importMembers = importMembers.toImmList()

    init {
        check(this.directMembers.isNotEmpty() || this.importMembers.isNotEmpty())
    }

    fun hasTag(tags: List<C_NamespaceMemberTag>): Boolean {
        return directMembers.any { it.hasTag(tags) } || importMembers.any { it.hasTag(tags) }
    }

    fun element(langVersion: R_LangVersion?, tags: List<C_NamespaceMemberTag> = immListOf()): C_NamespaceElement {
        return element0(langVersion, tags) ?: element0(langVersion, immListOf())!!
    }

    private fun element0(langVersion: R_LangVersion?, tags: List<C_NamespaceMemberTag>): C_NamespaceElement? {
        var members = directMembers.filter { it.hasTag(tags) }
            .ifEmpty { importMembers.filter { it.hasTag(tags) } }

        if (UNIQUE_ITEMS_SWITCH.isActive(langVersion)) {
            members = members.toSet().toImmList()
        }

        return when {
            members.isEmpty() -> null
            members.size == 1 -> C_NamespaceElement(members[0], immListOf())
            else -> C_NamespaceElement(members[0], members)
        }
    }

    companion object {
        private val UNIQUE_ITEMS_SWITCH = C_FeatureSwitch("0.13.5")
    }
}

sealed class C_Namespace {
    abstract fun getEntries(): Map<R_Name, C_NamespaceEntry>
    abstract fun getEntry(name: R_Name): C_NamespaceEntry?
    abstract fun getDocMembers(): Map<String, DocDefinition>

    fun getElement(
        name: R_Name,
        langVersion: R_LangVersion?,
        tags: List<C_NamespaceMemberTag> = immListOf(),
    ): C_NamespaceElement? {
        val entry = getEntry(name)
        return entry?.element(langVersion, tags)
    }

    companion object {
        val EMPTY: C_Namespace = C_BasicNamespace(immMapOf())

        fun makeLate(getter: LateGetter<C_Namespace>): C_Namespace {
            return C_LateNamespace(getter)
        }
    }
}

private class C_BasicNamespace(entries: Map<R_Name, C_NamespaceEntry>): C_Namespace() {
    private val entries = entries.toImmMap()

    private val docMembersLazy: Map<String, DocDefinition> by lazy {
        entries.entries.associateNotNullValues {
            val members = it.value.directMembers.ifEmpty { it.value.importMembers }
            it.key.str to members.singleOrNull()?.docDefinition
        }
    }

    override fun getEntries(): Map<R_Name, C_NamespaceEntry> {
        return entries
    }

    override fun getEntry(name: R_Name): C_NamespaceEntry? {
        return entries[name]
    }

    override fun getDocMembers(): Map<String, DocDefinition> = docMembersLazy
}

private class C_LateNamespace(private val getter: LateGetter<C_Namespace>): C_Namespace() {
    override fun getEntries() = getter.get().getEntries()
    override fun getEntry(name: R_Name) = getter.get().getEntry(name)
    override fun getDocMembers() = getter.get().getDocMembers()
}

class C_NamespaceBuilder {
    private val directMembers = mutableMultimapOf<R_Name, C_NamespaceMember>()
    private val importMembers = mutableMultimapOf<R_Name, C_NamespaceMember>()

    fun add(name: R_Name, member: C_NamespaceMember) {
        directMembers.put(name, member)
    }

    fun add(name: R_Name, entry: C_NamespaceEntry) {
        directMembers.putAll(name, entry.directMembers)
        importMembers.putAll(name, entry.importMembers)
    }

    fun build(): C_Namespace {
        val names = directMembers.keySet() + importMembers.keySet()
        val entries = names.associateWith {
            val directIts = directMembers.get(it).toImmList()
            val importIts = importMembers.get(it).toImmList()
            C_NamespaceEntry(directIts, importIts)
        }.toImmMap()
        return C_BasicNamespace(entries)
    }
}
