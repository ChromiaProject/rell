/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Name
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_MessageManager
import net.postchain.rell.base.compiler.base.utils.C_RNamePath
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_IdeName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.*

data class C_NameValue<T>(val name: C_Name, val value: T)

class C_Name private constructor(val pos: S_Pos, val rName: R_Name) {
    val str = rName.str

    override fun toString() = str

    companion object {
        fun make(pos: S_Pos, str: String): C_Name = C_Name(pos, R_Name.of(str))
        fun make(pos: S_Pos, rName: R_Name): C_Name = C_Name(pos, rName)
    }
}

class C_QualifiedName(val parts: ImmList<C_Name>) {
    init {
        check(this.parts.isNotEmpty())
    }

    val pos = this.parts.first().pos
    val last = this.parts.last()

    constructor(name: C_Name): this(immListOf(name))

    fun add(name: C_Name) = C_QualifiedName((parts + name).toImmList())
    fun parentPath() = parts.dropLast(1).toImmList()
    fun toRName() = R_QualifiedName(parts.mapToImmList { it.rName })
    fun toPath() = C_RNamePath.of(parts.mapToImmList { it.rName })
    fun toLazyPosString() = LazyPosString.of(last.pos) { str() }

    fun str() = parts.joinToString(".")
    override fun toString() = str()
}

class C_IdeName(val name: C_Name, val ideInfo: C_IdeSymbolInfo) {
    val pos = name.pos
    val str = name.str

    fun toRName(): R_IdeName = R_IdeName(name.rName, ideInfo)
    override fun toString() = name.toString()
}

class C_IdeQualifiedName(val parts: ImmList<C_IdeName>) {
    init {
        check(this.parts.isNotEmpty())
    }

    val last = this.parts.last()

    fun toCQualifiedName() = C_QualifiedName(parts.mapToImmList { it.name })
    fun toPath() = C_RNamePath.of(parts.mapToImmList { it.name.rName })

    fun str() = parts.joinToString(".") { it.name.str }
    override fun toString() = str()
}

sealed class C_IdeSymbolInfo {
    abstract val kind: IdeSymbolKind
    abstract val defId: IdeSymbolId?
    abstract val link: IdeSymbolLink?

    abstract fun getIdeInfo(): IdeSymbolInfo

    abstract fun transformDocSymbol(f: (DocSymbol) -> DocSymbol): C_IdeSymbolInfo
    protected abstract fun update0(kind: IdeSymbolKind, defId: IdeSymbolId?, link: IdeSymbolLink?): C_IdeSymbolInfo

    fun update(
        kind: IdeSymbolKind = this.kind,
        defId: IdeSymbolId? = this.defId,
        link: IdeSymbolLink? = this.link,
    ): C_IdeSymbolInfo {
        return if (kind == this.kind && defId === this.defId && link === this.link) this else {
            update0(kind = kind, defId = defId, link = link)
        }
    }

    companion object {
        private val KIND_MAP = IdeSymbolKind.entries
            .associateWithToImmMap { C_IdeSymbolInfo_Direct(IdeSymbolInfo.get(it)) }

        val UNKNOWN = get(IdeSymbolKind.UNKNOWN)
        val MEM_TUPLE_ATTR = get(IdeSymbolKind.MEM_TUPLE_ATTR)

        fun get(kind: IdeSymbolKind): C_IdeSymbolInfo = KIND_MAP.getValue(kind)

        fun direct(ideInfo: IdeSymbolInfo): C_IdeSymbolInfo = C_IdeSymbolInfo_Direct(ideInfo)

        fun direct(
            kind: IdeSymbolKind,
            defId: IdeSymbolId? = null,
            link: IdeSymbolLink? = null,
            doc: DocSymbol? = null,
        ): C_IdeSymbolInfo {
            val ideInfo = IdeSymbolInfo.make(kind = kind, defId = defId, link = link, doc = doc)
            return C_IdeSymbolInfo_Direct(ideInfo)
        }

        fun late(
            kind: IdeSymbolKind,
            defId: IdeSymbolId? = null,
            link: IdeSymbolLink? = null,
            docGetter: C_LateGetter<DocSymbol?>,
        ): C_IdeSymbolInfo {
            return C_IdeSymbolInfo_Late(kind, defId, link, docGetter)
        }
    }
}

private class C_IdeSymbolInfo_Direct(private val ideInfo: IdeSymbolInfo): C_IdeSymbolInfo() {
    override val kind: IdeSymbolKind get() = ideInfo.kind
    override val defId: IdeSymbolId? get() = ideInfo.defId
    override val link: IdeSymbolLink? get() = ideInfo.link

    override fun getIdeInfo(): IdeSymbolInfo = ideInfo

    override fun transformDocSymbol(f: (DocSymbol) -> DocSymbol): C_IdeSymbolInfo {
        val resDoc = f(ideInfo.doc ?: DocSymbol.NONE)
        val resIdeInfo = ideInfo.update(doc = resDoc)
        return C_IdeSymbolInfo_Direct(resIdeInfo)
    }

    override fun update0(kind: IdeSymbolKind, defId: IdeSymbolId?, link: IdeSymbolLink?): C_IdeSymbolInfo {
        val ideInfo2 = ideInfo.update(kind = kind, defId = defId, link = link)
        return if (ideInfo2 === ideInfo) this else C_IdeSymbolInfo_Direct(ideInfo2)
    }
}

private class C_IdeSymbolInfo_Late(
    override val kind: IdeSymbolKind,
    override val defId: IdeSymbolId?,
    override val link: IdeSymbolLink?,
    private val docGetter: C_LateGetter<DocSymbol?>,
): C_IdeSymbolInfo() {
    private val ideInfoLazy: IdeSymbolInfo by lazy {
        val doc = docGetter.get()
        IdeSymbolInfo.make(kind = kind, defId = defId, link = link, doc = doc)
    }

    override fun getIdeInfo(): IdeSymbolInfo = ideInfoLazy

    override fun transformDocSymbol(f: (DocSymbol) -> DocSymbol): C_IdeSymbolInfo {
        val resDocGetter = docGetter.transform { doc ->
            f(doc ?: DocSymbol.NONE)
        }
        return C_IdeSymbolInfo_Late(kind, defId, link, resDocGetter)
    }

    override fun update0(kind: IdeSymbolKind, defId: IdeSymbolId?, link: IdeSymbolLink?): C_IdeSymbolInfo {
        return C_IdeSymbolInfo_Late(kind = kind, defId = defId, link = link, docGetter = docGetter)
    }
}

class C_IdeSymbolDef(
    val defInfo: C_IdeSymbolInfo,
    val refInfo: C_IdeSymbolInfo,
) {
    companion object {
        fun make(
            kind: IdeSymbolKind,
            defId: IdeSymbolId? = null,
            link: IdeSymbolLink? = null,
            doc: DocSymbol? = null,
        ): C_IdeSymbolDef {
            val defInfo = C_IdeSymbolInfo.direct(kind, defId = defId, doc = doc)
            val refInfo = C_IdeSymbolInfo.direct(kind, link = link, doc = doc)
            return C_IdeSymbolDef(defInfo, refInfo)
        }

        fun make(kind: IdeSymbolKind, globalId: IdeSymbolGlobalId?, doc: DocSymbol? = null): C_IdeSymbolDef {
            val link = if (globalId == null) null else IdeGlobalSymbolLink(globalId)
            return make(kind, defId = globalId?.symId, link = link, doc = doc)
        }

        fun make(kind: IdeSymbolKind, pos: S_Pos, id: IdeSymbolId): C_IdeSymbolDef {
            val file = pos.idePath()
            return make(kind, file, id)
        }

        fun make(kind: IdeSymbolKind, file: IdeFilePath, id: IdeSymbolId, doc: DocSymbol? = null): C_IdeSymbolDef {
            val globalId = IdeSymbolGlobalId(file, id)
            return make(kind, globalId, doc)
        }

        fun makeLate(
            kind: IdeSymbolKind,
            file: IdeFilePath,
            id: IdeSymbolId,
            docGetter: C_LateGetter<DocSymbol?>,
        ): C_IdeSymbolDef {
            val globalId = IdeSymbolGlobalId(file, id)
            val link = IdeGlobalSymbolLink(globalId)
            return makeLate(kind, defId = id, link = link, docGetter = docGetter)
        }

        fun makeLate(
            kind: IdeSymbolKind,
            defId: IdeSymbolId? = null,
            link: IdeSymbolLink? = null,
            docGetter: C_LateGetter<DocSymbol?>,
        ): C_IdeSymbolDef {
            val defInfo = C_IdeSymbolInfo.late(kind, defId = defId, docGetter = docGetter)
            val refInfo = C_IdeSymbolInfo.late(kind, link = link, docGetter = docGetter)
            return C_IdeSymbolDef(defInfo, refInfo)
        }
    }
}

interface C_IdeSymbolInfoHandle {
    fun setIdeInfo(ideInfo: C_IdeSymbolInfo)

    private object C_NopIdeSymbolInfoHandle: C_IdeSymbolInfoHandle {
        override fun setIdeInfo(ideInfo: C_IdeSymbolInfo) {
            // Do nothing.
        }
    }

    companion object {
        val NOP_HANDLE: C_IdeSymbolInfoHandle = C_NopIdeSymbolInfoHandle
    }
}

class C_UniqueDefaultIdeInfoPtr(
    private val ideInfoHand: C_IdeSymbolInfoHandle,
    private val defaultIdeInfo: C_IdeSymbolInfo,
): C_IdeSymbolInfoHandle {
    private var resolved = false
    private var valid = true

    constructor(): this(C_IdeSymbolInfoHandle.NOP_HANDLE, C_IdeSymbolInfo.UNKNOWN)

    fun isValid(): Boolean = valid

    fun setDefault() {
        checkState()
        ideInfoHand.setIdeInfo(defaultIdeInfo)
    }

    override fun setIdeInfo(ideInfo: C_IdeSymbolInfo) {
        checkState()
        ideInfoHand.setIdeInfo(ideInfo)
    }

    fun setIdeInfoOrDefault(ideInfo: C_IdeSymbolInfo?) {
        checkState()
        ideInfoHand.setIdeInfo(ideInfo ?: defaultIdeInfo)
    }

    fun move(): C_UniqueDefaultIdeInfoPtr {
        check(!resolved)
        check(valid)
        valid = false
        return C_UniqueDefaultIdeInfoPtr(ideInfoHand, defaultIdeInfo)
    }

    private fun checkState() {
        check(!resolved)
        resolved = true
    }
}

sealed class C_NameHandle(val pos: S_Pos, val rName: R_Name): C_IdeSymbolInfoHandle {
    val str = rName.str
    val name = C_Name.make(pos, rName)

    /** Use in special cases, when there is no other way. Setting a default info means there will be no error if one
     * forgets to set the info. */
    abstract fun setDefaultIdeInfo(ideInfo: C_IdeSymbolInfo)
}

class C_QualifiedNameHandle(parts: List<C_NameHandle>) {
    val parts = parts.let {
        check(it.isNotEmpty())
        it.toImmList()
    }

    val pos = this.parts.first().pos
    val last = this.parts.last()
    val size: Int get() = this.parts.size
    val first: C_NameHandle get() = this.parts.first()

    val cName: C_QualifiedName by lazy {
        C_QualifiedName(this.parts.mapToImmList { it.name })
    }

    val rName: R_QualifiedName by lazy {
        R_QualifiedName(this.parts.mapToImmList { it.rName })
    }

    constructor(name: C_NameHandle): this(immListOf(name))

    fun setIdeInfo(infos: List<C_IdeSymbolInfo>) {
        checkEquals(infos.size, parts.size)
        infos.forEachIndexed { index, info ->
            parts[index].setIdeInfo(info)
        }
    }

    fun setIdeInfo(info: C_IdeSymbolInfo) {
        parts.forEach {
            it.setIdeInfo(info)
        }
    }

    fun str(): String = cName.str()

    override fun toString() = str()
}

sealed class C_NameContext(val msgMgr: C_MessageManager, val compilerOptions: C_CompilerOptions) {
    abstract fun addName(sName: S_Name, rName: R_Name): C_NameHandle
    abstract fun addSymbol(pos: S_Pos, ideInfo: C_IdeSymbolInfo)
    abstract fun setDefId(pos: S_Pos, defId: IdeSymbolId)
    abstract fun setLink(pos: S_Pos, link: IdeSymbolLink)
}

private class C_NameContext_Nop(
    msgMgr: C_MessageManager,
    compilerOptions: C_CompilerOptions,
): C_NameContext(msgMgr, compilerOptions) {
    override fun addName(sName: S_Name, rName: R_Name): C_NameHandle {
        return C_NameHandle_Nop(sName.pos, rName)
    }

    override fun addSymbol(pos: S_Pos, ideInfo: C_IdeSymbolInfo) {
        // Do nothing.
    }

    override fun setDefId(pos: S_Pos, defId: IdeSymbolId) {
        // Do nothing.
    }

    override fun setLink(pos: S_Pos, link: IdeSymbolLink) {
        // Do nothing.
    }

    private class C_NameHandle_Nop(pos: S_Pos, rName: R_Name): C_NameHandle(pos, rName) {
        override fun setIdeInfo(ideInfo: C_IdeSymbolInfo) {
            // Do nothing.
        }

        override fun setDefaultIdeInfo(ideInfo: C_IdeSymbolInfo) {
            // Do nothing.
        }
    }
}

private class C_NameContext_Active(
    msgMgr: C_MessageManager,
    compilerOptions: C_CompilerOptions,
): C_NameContext(msgMgr, compilerOptions) {
    private val checkDefIdConflicts = compilerOptions.ideDefIdConflictError

    private val nameMap = mutableMapOf<S_Pos, C_NameHandle_Active>()
    private val symbolMap = mutableMapOf<S_Pos, C_IdeSymbolInfo>()
    private val extraMap = mutableMapOf<S_Pos, ExtraInfo>()

    override fun addName(sName: S_Name, rName: R_Name): C_NameHandle {
        val pos = sName.pos
        checkTest(pos !in symbolMap)

        val oldHand = nameMap[pos]
        if (oldHand != null) {
            oldHand.redefinition()
            return oldHand
        }

        val hand = C_NameHandle_Active(pos, rName)
        nameMap[pos] = hand
        return hand
    }

    override fun addSymbol(pos: S_Pos, ideInfo: C_IdeSymbolInfo) {
        checkTest(pos !in symbolMap)
        checkTest(pos !in nameMap)
        symbolMap[pos] = ideInfo
    }

    override fun setDefId(pos: S_Pos, defId: IdeSymbolId) {
        val extra = extraMap.computeIfAbsent(pos) { ExtraInfo() }
        checkTest(extra.defId == null) { "name not found: $pos" }
        extra.defId = defId
    }

    override fun setLink(pos: S_Pos, link: IdeSymbolLink) {
        val extra = extraMap.computeIfAbsent(pos) { ExtraInfo() }
        checkTest(extra.link == null) { "name not found: $pos" }
        extra.link = link
    }

    private fun finish0(): ImmMap<S_Pos, IdeSymbolInfo> {
        finishExtra()

        val res = mutableMapOf<S_Pos, IdeSymbolInfo>()

        for ((pos, info) in symbolMap) {
            res[pos] = info.getIdeInfo()
        }

        for (hand in nameMap.values) {
            val ideInfo = hand.ideInfo()
            if (ideInfo != null) {
                checkTest(hand.pos !in res)
                res[hand.pos] = ideInfo
            }
        }

        val immRes = res.toImmMap()

        if (CommonUtils.IS_UNIT_TEST && checkDefIdConflicts) {
            checkDefIdConflicts(immRes)
        }

        return immRes
    }

    private fun finishExtra() {
        for ((pos, extra) in extraMap) {
            val nameHand = nameMap[pos]
            checkTest(nameHand != null) { "name not found: $pos" }
            val defId = extra.defId
            val link = extra.link
            if (defId != null) {
                nameHand?.setDefId(defId)
            }
            if (link != null) {
                nameHand?.setLink(link)
            }
        }
    }

    private fun checkDefIdConflicts(map: Map<S_Pos, IdeSymbolInfo>) {
        val fileMap = mutableMapOf<C_SourcePath, MutableMap<IdeSymbolId, S_Pos>>()
        for ((pos, info) in map) {
            if (info.defId != null) {
                val path = pos.path()
                val subMap = fileMap.computeIfAbsent(path) { mutableMapOf() }
                val oldPos = subMap.put(info.defId, pos)
                check(oldPos == null) { "$oldPos $pos ${info.defId}" }
            }
        }
    }

    private class ExtraInfo {
        var defId: IdeSymbolId? = null
        var link: IdeSymbolLink? = null
    }

    private class C_NameHandle_Active(pos: S_Pos, rName: R_Name): C_NameHandle(pos, rName) {
        private val initStack = Exception("Stack")

        private var mIdeInfo: C_IdeSymbolInfo? = null
        private var mDefaultIdeInfo: C_IdeSymbolInfo? = null
        private var ideInfoStack: Throwable? = null

        fun redefinition() {
            if (CommonUtils.IS_UNIT_TEST) {
                throw RuntimeException("Name already compiled: $rName (Stack 2)", initStack)
            }
        }

        fun ideInfo(): IdeSymbolInfo? {
            val resIdeInfo = mIdeInfo ?: mDefaultIdeInfo
            if (resIdeInfo == null && CommonUtils.IS_UNIT_TEST) {
                throw IllegalStateException("No IDE info: $rName", initStack)
            }
            return resIdeInfo?.getIdeInfo()
        }

        override fun setDefaultIdeInfo(ideInfo: C_IdeSymbolInfo) {
            check(mDefaultIdeInfo == null)
            check(mIdeInfo == null)
            mDefaultIdeInfo = ideInfo
        }

        override fun setIdeInfo(ideInfo: C_IdeSymbolInfo) {
            if (CommonUtils.IS_UNIT_TEST) {
                if (mIdeInfo != null) {
                    val msg = "Ide info already set: $rName (old ${mIdeInfo!!.kind} new ${ideInfo.kind})"
                    throw RuntimeException(msg, ideInfoStack)
                }
                ideInfoStack = Exception("Stack 1")
            }
            if (mIdeInfo == null) {
                mIdeInfo = ideInfo
            }
        }

        fun setDefId(defId: IdeSymbolId) {
            val ideInfo = mIdeInfo
            when {
                ideInfo == null -> failTest()
                ideInfo.defId != null -> failTest()
                else -> mIdeInfo = ideInfo.update(defId = defId)
            }
        }

        fun setLink(link: IdeSymbolLink) {
            val ideInfo = mIdeInfo
            when {
                ideInfo == null -> failTest()
                ideInfo.link != null -> failTest()
                else -> mIdeInfo = ideInfo.update(link = link)
            }
        }
    }

    fun finish(): ImmMap<S_Pos, IdeSymbolInfo> {
        return finish0()
    }
}

class C_NameContextManager(
    private val msgMgr: C_MessageManager,
    private val opts: C_CompilerOptions,
) {
    val nopNameCtx: C_NameContext = C_NameContext_Nop(msgMgr, opts)

    private val activeNameCtx0 = C_NameContext_Active(msgMgr, opts)
    val activeNameCtx: C_NameContext = activeNameCtx0

    fun finish(): ImmMap<S_Pos, IdeSymbolInfo> {
        return activeNameCtx0.finish()
    }
}

private fun checkTest(b: Boolean) {
    if (CommonUtils.IS_UNIT_TEST) {
        check(b)
    }
}

private fun checkTest(b: Boolean, msg: () -> String) {
    if (CommonUtils.IS_UNIT_TEST) {
        check(b, msg)
    }
}

private fun failTest() {
    check(!CommonUtils.IS_UNIT_TEST)
}
