/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.utils.C_FeatureRestrictions
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.parser.RellTokenMatch
import net.postchain.rell.base.model.R_FilePos
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocSourcePos
import net.postchain.rell.base.utils.ide.IdeFilePath
import java.util.*
import java.util.function.Supplier

abstract class S_Pos: Comparable<S_Pos> {
    abstract fun path(): C_SourcePath
    abstract fun idePath(): IdeFilePath
    abstract fun offset(): Int
    abstract fun line(): Int
    abstract fun column(): Int

    fun str() = "${path()}(${line()}:${column()})"
    fun strLine() = "${path()}:${line()}"

    fun toFilePos() = R_FilePos(path().str(), line())
    fun toDocPos() = DocSourcePos(path().str(), line())

    final override fun compareTo(other: S_Pos): Int {
        var d = path().compareTo(other.path())
        if (d == 0) d = offset().compareTo(other.offset())
        return d
    }

    final override fun toString() = str()
}

class S_BasicPos(
    private val file: C_ParserFilePath,
    private val offset: Int,
    private val row: Int,
    private val col: Int,
): S_Pos() {
    override fun path() = file.sourcePath
    override fun idePath() = file.idePath
    override fun offset() = offset
    override fun line() = row
    override fun column() = col

    override fun equals(other: Any?): Boolean {
        return other is S_BasicPos
                && offset == other.offset
                && row == other.row
                && col == other.col
                && file == other.file
    }

    override fun hashCode(): Int {
        return Objects.hash(offset, row, col, file)
    }
}

data class S_PosRange(val start: S_Pos, val end: S_Pos) {
    init {
        checkEquals(end.path(), start.path())
        check(end >= start) { "$start $end" }
    }

    override fun toString() = "${start.path()}(${start.line()}:${start.column()}-${end.line()}:${end.column()})"
}

abstract class S_Node {
    val attachment: Any? = getAttachment()

    companion object {
        private val ATTACHMENT_PROVIDER_LOCAL = ThreadLocalContext<Supplier<Any?>>(Supplier { null })

        @JvmStatic
        fun runWithAttachmentProvider(provider: Supplier<Any?>, code: Runnable) {
            ATTACHMENT_PROVIDER_LOCAL.set(provider) {
                code.run()
            }
        }

        private fun getAttachment(): Any? {
            val provider = ATTACHMENT_PROVIDER_LOCAL.get()
            val res = provider.get()
            return res
        }
    }
}

data class S_PosValue<T>(val pos: S_Pos, val value: T) {
    constructor(t: RellTokenMatch, value: T): this(t.pos, value)

    override fun toString() = value.toString()
}

class S_Name(val pos: S_Pos, private val rName: R_Name): S_Node() {
    private val str = rName.str

    fun compile(ctx: C_NameContext, def: Boolean = false): C_NameHandle {
        if (def) {
            val resName = RESERVED_NAMES[rName]
            resName?.access(ctx, pos, rName)
        }
        return ctx.addName(this, rName)
    }

    fun compile(ctx: C_SymbolContext, def: Boolean = false) = compile(ctx.nameCtx, def = def)
    fun compile(ctx: C_MountContext, def: Boolean = false) = compile(ctx.symCtx, def = def)
    fun compile(ctx: C_ExprContext, def: Boolean = false) = compile(ctx.symCtx, def = def)

    fun getRNameSpecial(): R_Name {
        // This method shall be called only in special cases. Whenever possible, one of compile(...) methods must be
        // used in order to add the name to the context and attach IDE meta-information to the name.
        return rName
    }

    override fun toString() = str

    companion object {
        private val OLD_KEYWORDS: Map<R_Name, C_ReservedName> =
            mapOf(
                "list" to "0.11.0",
                "set" to "0.11.0",
                "map" to "0.11.0",
            )
            .mapKeys { R_Name.of(it.key) }
            .mapValues {
                val name = it.key.str
                val restrictions = C_FeatureRestrictions.make(it.value, name, "Name '$name' is")
                C_ReservedName(restrictions, null)
            }
            .toImmMap()

        private const val NEW_KWS_SINCE = "0.13.12"

        private val NEW_KEYWORDS: Map<R_Name, C_ReservedName> =
            mapOf(
                "alias" to NEW_KWS_SINCE,
                "as" to NEW_KWS_SINCE,
                "catch" to NEW_KWS_SINCE,
                "const" to NEW_KWS_SINCE,
                "finally" to NEW_KWS_SINCE,
                "final" to NEW_KWS_SINCE,
                "fun" to NEW_KWS_SINCE,
                "internal" to NEW_KWS_SINCE,
                "is" to NEW_KWS_SINCE,
                "native" to NEW_KWS_SINCE,
                "private" to NEW_KWS_SINCE,
                "protected" to NEW_KWS_SINCE,
                "savepoint" to NEW_KWS_SINCE,
                "sealed" to NEW_KWS_SINCE,
                "static" to NEW_KWS_SINCE,
                "super" to NEW_KWS_SINCE,
                "this" to NEW_KWS_SINCE,
                "throw" to NEW_KWS_SINCE,
                "trait" to NEW_KWS_SINCE,
                "transact" to NEW_KWS_SINCE,
                "try" to NEW_KWS_SINCE,
                "typealias" to NEW_KWS_SINCE,
                "yield" to NEW_KWS_SINCE,
            )
            .mapKeys { R_Name.of(it.key) }
            .mapValues { C_ReservedName(null, RellVersions.parse(it.value)) }
            .toImmMap()

        private val RESERVED_NAMES: Map<R_Name, C_ReservedName> = OLD_KEYWORDS.unionNoConflicts(NEW_KEYWORDS)

        private class C_ReservedName(val oldRestrictions: C_FeatureRestrictions?, val newVersion: R_LangVersion?) {
            fun access(ctx: C_NameContext, pos: S_Pos, rName: R_Name) {
                oldRestrictions?.access(ctx.msgMgr, pos, "name", ctx.compilerOptions)

                val version = ctx.compilerOptions.compatibility
                if (newVersion != null && version != null && version >= newVersion) {
                    val msg = "Name '$rName' is reserved"
                    ctx.msgMgr.error(pos, "name:reserved:$rName:$newVersion", msg)
                }
            }
        }
    }
}

class S_QualifiedName(parts: List<S_Name>): S_Node() {
    val parts = parts.toImmList()

    init {
        check(this.parts.isNotEmpty())
    }

    val pos = this.parts.first().pos
    val last = this.parts.last()

    constructor(name: S_Name): this(immListOf(name))

    fun add(name: S_Name) = S_QualifiedName(parts + name)

    fun str() = parts.joinToString(".")
    override fun toString() = str()

    fun compile(ctx: C_NameContext, def: Boolean = false): C_QualifiedNameHandle {
        val cParts = parts.map { it.compile(ctx, def = def) }
        return C_QualifiedNameHandle(cParts)
    }

    fun compile(ctx: C_SymbolContext) = compile(ctx.nameCtx)
    fun compile(ctx: C_ExprContext) = compile(ctx.nameCtx)
    fun compile(ctx: C_DefinitionContext) = compile(ctx.symCtx)
}

class S_Comment(
    private val tokenPos: S_Pos,
    private val text: String,
) {
    // Using token pos for now.
    val pos = tokenPos

    init {
        require(text.startsWith("/**") && text.endsWith("*/") && text.length >= 5) { text }
    }

    fun compile(docFactory: C_DocSymbolFactory): DocComment? {
        val rawText = text.substring(3, text.length - 2).trim()
        return docFactory.compileComment(tokenPos, rawText)
    }

    override fun toString() = text
}

fun S_Comment?.compileGetter(docFactory: C_DocSymbolFactory): C_LateGetter<DocComment?> {
    val docComment = this?.compile(docFactory)
    return C_LateGetter.const(docComment)
}

fun S_Comment?.compileGetter(symCtx: C_SymbolContext): C_LateGetter<DocComment?> {
    return compileGetter(symCtx.docSymbolFactory)
}
