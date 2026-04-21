/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.utils.*
import java.util.*
import kotlin.math.min

@JvmInline value class Name(val str: String): Comparable<Name> {
    init {
        require(isValid(str)) { str }
    }

    override fun compareTo(other: Name) = str.compareTo(other.str)
    override fun toString() = str

    companion object {
        fun isNameStart(c: Char) = c == '_' || c in 'A'..'Z' || c in 'a'..'z'
        fun isNamePart(c: Char) = isNameStart(c) || c in '0'..'9'

        fun isValid(s: String): Boolean {
            if (s.isEmpty()) return false
            if (!isNameStart(s[0])) return false
            for (c in s) {
                if (!isNamePart(c)) return false
            }
            return true
        }

        fun of(s: String): Name = Name(s)

        fun ofOpt(s: String): Name? = if (isValid(s))
            Name(s)
        else {
            null
        }

        fun listOfOpt(s: String): List<Name>? {
            val parts = s.split('.')
            val names = parts.map { ofOpt(it) }
            val names2 = names.filterNotNull()
            if (names2.size != names.size) return null
            return names2.toImmList()
        }
    }
}

// ---- Qualified name types ----

sealed class GenericQualifiedName<T: GenericQualifiedName<T>>: Comparable<T> {
    abstract val parts: ImmList<Name>
    private val str by lazy { parts.joinToString(".") }

    fun str() = str
    fun displayStr() = str.ifEmpty { "''" }
    fun isEmpty() = parts.isEmpty()
    fun size() = parts.size

    fun startsWith(qName: T): Boolean {
        val prefParts = qName.parts
        if (prefParts.size > parts.size) return false
        for (i in prefParts.indices) {
            if (parts[i] != prefParts[i]) return false
        }
        return true
    }

    fun append(name: Name): T = create(parts + name)

    fun append(name: String): T {
        val rName = Name.of(name)
        return append(rName)
    }

    protected abstract fun self(): T
    protected abstract fun create(parts: List<Name>): T

    final override fun compareTo(other: T): Int {
        return compareLists(parts, other.parts)
    }

    final override fun toString() = str()

    final override fun equals(other: Any?) = other === this
            || (other is GenericQualifiedName<*> && javaClass == other.javaClass && parts == other.parts)
    final override fun hashCode() = Objects.hash(javaClass, parts)
}

data class QualifiedName(override val parts: ImmList<Name>): GenericQualifiedName<QualifiedName>() {
    constructor(parts: List<Name>) : this(parts.toImmList())

    init {
        check(parts.isNotEmpty()) { "Empty qualified name." }
    }

    val first: Name = this.parts.first()
    val last: Name = this.parts.last()

    override fun self() = this
    override fun create(parts: List<Name>) = QualifiedName(parts.toImmList())

    fun replaceLast(name: Name): QualifiedName {
        if (name == last) return this
        val resParts = parts.subList(0, parts.size - 1) + name
        return QualifiedName(resParts)
    }

    companion object {
        fun of(s: String): QualifiedName = requireNotNull(ofOpt(s)) { s }
        fun ofOpt(s: String): QualifiedName? = qNameOfOpt0(s, null) { QualifiedName(it.toImmList()) }
        fun of(parts: List<Name>): QualifiedName = QualifiedName(parts.toImmList())
    }
}

data class ModuleName(override val parts: ImmList<Name>): GenericQualifiedName<ModuleName>() {
    constructor(parts: List<Name>) : this(parts.toImmList())

    override fun self() = this
    override fun create(parts: List<Name>) = of(parts)

    fun parent(): ModuleName {
        val res = parentOrNull()
        return checkNotNull(res) { "Trying to get a parent name of an empty name" }
    }

    fun parentOrNull(): ModuleName? {
        return if (parts.isEmpty()) null else of(parts.subList(0, parts.size - 1))
    }

    companion object {
        val EMPTY = ModuleName(immListOf())
        fun of(parts: List<Name>) = if (parts.isEmpty()) EMPTY else ModuleName(parts)
        fun of(s: String) = qNameOf0(s, EMPTY) { ModuleName(it.toImmList()) }
        fun ofOpt(s: String) = qNameOfOpt0(s, EMPTY) { ModuleName(it.toImmList()) }
    }
}

data class MountName(override val parts: ImmList<Name>): GenericQualifiedName<MountName>() {
    constructor(parts: List<Name>) : this(parts.toImmList())

    override fun self() = this
    override fun create(parts: List<Name>) = MountName(parts)

    companion object {
        val EMPTY = MountName(immListOf())
        fun of(s: String) = qNameOf0(s, EMPTY) { MountName(it) }
        fun ofOpt(s: String) = qNameOfOpt0(s, EMPTY) { MountName(it) }
    }
}

data class FullName(
    val moduleName: ModuleName,
    val qualifiedName: QualifiedName,
) {
    val last: Name
        get() = qualifiedName.last

    fun str(): String = "$moduleName:$qualifiedName"
    override fun toString() = str()

    fun append(name: Name): FullName {
        val qName2 = qualifiedName.append(name)
        return FullName(moduleName, qName2)
    }

    fun replaceLast(name: Name): FullName {
        val qName2 = qualifiedName.replaceLast(name)
        return FullName(moduleName, qName2)
    }

    companion object {
        fun of(s: String): FullName {
            val i = s.indexOf(':')
            require(i >= 0) { s }
            val moduleName = ModuleName.of(s.substring(0, i))
            val qName = QualifiedName.of(s.substring(i + 1))
            return FullName(moduleName, qName)
        }
    }
}

// ---- Position and identity types ----

data class DefinitionId(val module: String, val definition: String) {
    override fun toString() = str(module, definition)

    companion object {
        val ERROR = DefinitionId("<error>", "<error>")

        fun str(module: String, definition: String) = "$module:$definition"
    }
}

data class FilePos(val file: String, val line: Int) {
    override fun toString() = "$file:$line"
}

data class ErrorPos(val file: String, val line: Int) {
    constructor(filePos: FilePos): this(filePos.file, filePos.line)
    override fun toString() = "$file:$line"
}

class R_StackPos(val def: DefinitionId, val file: FilePos) {
    override fun toString() = "$def($file)"
}

// ---- Definition name and module key ----

data class DefinitionName(
    val module: String,
    val qualifiedName: String,
    val simpleName: String,
) {
    val appLevelName = appLevelName(module, qualifiedName)

    val strictAppLevelName: String by lazy {
        if (module.isEmpty()) ":$appLevelName" else appLevelName
    }

    constructor(fullName: FullName): this(fullName.moduleName.str(), fullName.qualifiedName.str(), fullName.last.str)

    override fun toString() = appLevelName

    companion object {
        fun appLevelName(module: String, qualifiedName: String): String {
            return if (module.isEmpty()) qualifiedName else DefinitionId.str(module, qualifiedName)
        }
    }
}

data class ModuleKey(val name: ModuleName, val externalChain: String?) {
    fun str() = str(name, externalChain)
    override fun toString() = str()

    companion object {
        val EMPTY = ModuleKey(ModuleName.EMPTY, null)

        fun str(name: ModuleName, externalChain: String?): String {
            return if (externalChain == null) name.toString() else "$name[$externalChain]"
        }
    }
}

// ---- Debug identity UIDs ----

@JvmInline
value class AppUid(val id: Long) {
    override fun toString() = "App[$id]"
}

class R_ContainerUid(val id: Long, val app: AppUid) {
    override fun toString(): String {
        val params = listOf(id.toString()).filter { it.isNotEmpty() }.joinToString(",")
        return "$app/Container[$params]"
    }
}

class R_FnUid(val id: Long, private val container: R_ContainerUid) {
    override fun toString() = "$container/Fn[$id]"
}

class R_FrameBlockUid(val id: Long, val fn: R_FnUid) {
    override fun toString() = "$fn/Block[$id]"
}

// ---- At-expression IDs ----

data class R_AtExprId(val id: Long) {
    fun toRawString() = "$id"
    override fun toString() = "AtExpr[$id]"
}

data class R_AtEntityId(val exprId: R_AtExprId, val id: Long) {
    override fun toString() = "AtEntity[${exprId.toRawString()}:$id]"
}

// ---- Entity flags ----

data class EntityFlags(
    val isObject: Boolean,
    val canCreate: Boolean,
    val canUpdate: Boolean,
    val canDelete: Boolean,
    val gtv: Boolean,
    val log: Boolean,
)

// ---- Key/Index kind enum ----

enum class KeyIndexKind(val code: String) {
    KEY("key"),
    INDEX("index"),
    ;

    val nameMsg = MsgString(code)
}

// ---- Global constant ID ----

data class GlobalConstantId(
    val index: Int,
    val app: AppUid,
    val module: ModuleKey,
    val appLevelName: String,
    private val moduleLevelName: String,
) {
    fun strCode() = "$index:$module:$moduleLevelName"

    override fun toString() = "$index:$app:$module:$moduleLevelName"

    // not overriding equals() and hashCode() on purpose
}

// ---- Operation modifiers ----

data class OperationModifiers(val isCompound: Boolean, val isSingular: Boolean) {
    companion object {
        private val DEFAULT_INSTANCE = OperationModifiers(isCompound = false, isSingular = false)

        fun getInstance(isCompound: Boolean, isSingular: Boolean): OperationModifiers =
            if (!isCompound && !isSingular) DEFAULT_INSTANCE else OperationModifiers(isCompound, isSingular)
    }
}

// ---- Type flags ----

data class GtvCompatibility(val fromGtv: Boolean, val toGtv: Boolean)

data class TypeFlags(
    val pure: Boolean,
    val mutable: Boolean,
    val gtv: GtvCompatibility,
    val virtualable: Boolean,
    val mixedTuple: Boolean,
    val hasTypeVariable: Boolean,
) {
    companion object {
        fun combine(flags: Collection<TypeFlags>): TypeFlags {
            var pure = true
            var mutable = false
            var fromGtv = true
            var toGtv = true
            var virtualable = true
            var mixedTuple = false
            var hasTypeVariable = false

            for (f in flags) {
                pure = pure && f.pure
                mutable = mutable || f.mutable
                fromGtv = fromGtv && f.gtv.fromGtv
                toGtv = toGtv && f.gtv.toGtv
                virtualable = virtualable && f.virtualable
                mixedTuple = mixedTuple || f.mixedTuple
                hasTypeVariable = hasTypeVariable || f.hasTypeVariable
            }

            return TypeFlags(
                pure = pure,
                mutable = mutable,
                gtv = GtvCompatibility(fromGtv, toGtv),
                virtualable = virtualable,
                mixedTuple = mixedTuple,
                hasTypeVariable = hasTypeVariable,
            )
        }
    }
}

// ---- Private helpers ----

private fun <T: GenericQualifiedName<T>> qNameOf0(s: String, empty: T, create: (List<Name>) -> T): T {
    val res = qNameOfOpt0(s, empty, create)
    return requireNotNull(res) { s }
}

private fun <T: GenericQualifiedName<T>> qNameOfOpt0(s: String, empty: T?, create: (List<Name>) -> T): T? {
    if (s == "") return empty
    val parts = Name.listOfOpt(s)
    return if (parts == null) null else create(parts)
}

private fun <T: Comparable<T>> compareLists(l1: List<T>, l2: List<T>): Int {
    val n1 = l1.size
    val n2 = l2.size
    for (i in 0 until min(n1, n2)) {
        val c = l1[i].compareTo(l2[i])
        if (c != 0) {
            return c
        }
    }
    return n1.compareTo(n2)
}
