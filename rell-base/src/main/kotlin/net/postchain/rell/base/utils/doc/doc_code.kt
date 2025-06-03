/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import java.util.regex.Pattern

class DocCode private constructor(private val tokens: ImmList<DocCodeToken>) {
    fun strCode(): String = tokens.joinToString("") { it.strCode() }
    fun strRaw(): String = tokens.joinToString("") { it.strRaw() }

    fun visit(visitor: DocCodeTokenVisitor) {
        for (token in tokens) {
            token.visit(visitor)
        }
    }

    override fun toString() = strRaw()

    class Builder {
        private val tokens = mutableListOf<DocCodeToken>()

        fun build(): DocCode {
            return if (tokens.isEmpty()) EMPTY else DocCode(tokens.toImmList())
        }

        fun append(code: DocCode) = apply {
            tokens.addAll(code.tokens)
        }

        fun newline() = raw("\n")
        fun tab() = apply { tokens.add(DocCodeToken_Tab) }

        fun raw(s: String) = apply {
            if (s.isNotEmpty()) {
                tokens.add(DocCodeToken_Raw(s, s))
            }
        }

        fun sep(s: String) = apply {
            if (s.isNotEmpty()) {
                var s0 = s.trim()
                if (s0.isEmpty()) s0 = s.substring(0, 1)
                tokens.add(DocCodeToken_Raw(s, s0))
            }
        }

        fun keyword(s: String) = apply { tokens.add(DocCodeToken_Keyword(s)) }
        fun link(s: String) = apply { tokens.add(DocCodeToken_Link(s)) }
    }

    companion object {
        val EMPTY = DocCode(immListOf())

        fun builder(): Builder = Builder()

        fun raw(s: String): DocCode = Builder().raw(s).build()
        fun link(s: String): DocCode = Builder().link(s).build()
    }
}

interface DocCodeTokenVisitor {
    fun tab()
    fun raw(s: String)
    fun keyword(s: String)
    fun link(s: String)
}

sealed class DocCodeToken {
    abstract fun strCode(): String
    abstract fun strRaw(): String
    abstract fun visit(visitor: DocCodeTokenVisitor)
}

private object DocCodeToken_Tab: DocCodeToken() {
    override fun strCode() = "\t"
    override fun strRaw() = "\t"
    override fun visit(visitor: DocCodeTokenVisitor) = visitor.tab()
}

private class DocCodeToken_Raw(private val s: String, private val s0: String): DocCodeToken() {
    init {
        require(s.isNotEmpty())
        require(s0.isNotEmpty())
    }

    override fun strCode() = s
    override fun strRaw() = s0
    override fun visit(visitor: DocCodeTokenVisitor) = visitor.raw(s)
}

private class DocCodeToken_Keyword(private val s: String): DocCodeToken() {
    init {
        require(REGEX.matcher(s).matches()) { "<$s>" }
    }

    override fun strCode() = "<$s>"
    override fun strRaw() = s
    override fun visit(visitor: DocCodeTokenVisitor) = visitor.keyword(s)

    companion object {
        private val REGEX = Pattern.compile("[_a-z][_a-z0-9]*")
    }
}

private class DocCodeToken_Link(private val s: String): DocCodeToken() {
    override fun strCode() = "[$s]"
    override fun strRaw() = s
    override fun visit(visitor: DocCodeTokenVisitor) = visitor.link(s)
}
