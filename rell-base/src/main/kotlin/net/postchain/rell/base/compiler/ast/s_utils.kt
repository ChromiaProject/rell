/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.utils.C_FeatureRestrictions
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.parser.RellTokenMatch
import net.postchain.rell.base.model.R_FilePos
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ThreadLocalContext
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap
import java.util.*
import java.util.function.Supplier

abstract class S_Pos: Comparable<S_Pos> {
    abstract fun path(): C_SourcePath
    abstract fun idePath(): IdeFilePath
    abstract fun line(): Int
    abstract fun column(): Int

    fun str() = "${path()}(${line()}:${column()})"
    fun strLine() = "${path()}:${line()}"

    fun toFilePos() = R_FilePos(path().str(), line())

    final override fun compareTo(other: S_Pos): Int {
        var d = path().compareTo(other.path())
        if (d == 0) d = line().compareTo(other.line())
        if (d == 0) d = column().compareTo(other.column())
        return d
    }

    final override fun toString() = str()
}

class S_BasicPos(
    private val file: C_ParserFilePath,
    private val row: Int,
    private val col: Int,
): S_Pos() {
    override fun path() = file.sourcePath
    override fun idePath() = file.idePath
    override fun line() = row
    override fun column() = col

    override fun equals(other: Any?): Boolean {
        return other is S_BasicPos && row == other.row && col == other.col && file == other.file
    }

    override fun hashCode(): Int {
        return Objects.hash(row, col, file)
    }
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

data class S_NameOptValue<T>(val name: S_Name?, val value: T)

class S_Name(val pos: S_Pos, private val rName: R_Name): S_Node() {
    private val str = rName.str

    fun compile(ctx: C_SymbolContext, def: Boolean = false): C_NameHandle {
        if (def) {
            val restrictions = NAME_RESTRICTIONS[rName]
            restrictions?.access(ctx.msgMgr, pos, "name", ctx.compilerOptions)
        }
        return ctx.addName(this, rName)
    }

    fun compile(ctx: C_MountContext, def: Boolean = false) = compile(ctx.symCtx, def = def)
    fun compile(ctx: C_ExprContext, def: Boolean = false) = compile(ctx.symCtx, def = def)

    fun getRNameSpecial(): R_Name {
        // This method shall be called only in special cases. Whenever possible, one of compile(...) methods must be
        // used in order to add the name to the context and attach IDE meta-information to the name.
        return rName
    }

    override fun toString() = str

    companion object {
        private val NAME_RESTRICTIONS: Map<R_Name, C_FeatureRestrictions> =
            mapOf(
                "list" to "0.11.0",
                "set" to "0.11.0",
                "map" to "0.11.0",
            )
            .mapKeys { R_Name.of(it.key) }
            .mapValues {
                val name = it.key.str
                C_FeatureRestrictions.make(it.value, name, "Name '$name' is")
            }
            .toImmMap()
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

    fun compile(ctx: C_SymbolContext, def: Boolean = false): C_QualifiedNameHandle {
        val cParts = parts.map { it.compile(ctx, def = def) }
        return C_QualifiedNameHandle(cParts)
    }

    fun compile(ctx: C_ExprContext) = compile(ctx.symCtx)
    fun compile(ctx: C_DefinitionContext) = compile(ctx.symCtx)
}
