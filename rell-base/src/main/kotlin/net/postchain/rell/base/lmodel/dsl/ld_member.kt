/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lmodel.L_MemberHeader
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.doc.*

class Ld_MemberHeader(
    val since: R_LangVersion?,
    val comment: String?,
) {
    fun update(since: R_LangVersion? = this.since, comment: String? = this.comment): Ld_MemberHeader {
        return if (since == this.since && comment == this.comment) this else Ld_MemberHeader(since, comment)
    }

    fun finish(modCfg: Ld_ModuleConfig, fullName: R_FullName, requireSince: Boolean = true): L_MemberHeader {
        if (modCfg.requireSince && requireSince && since == null) {
            throw Ld_Exception("missing_since:$fullName", "Missing 'since' for '$fullName'")
        }

        val actualSince = if (modCfg.versionControl) since else null
        val docComment = docComment()
        return L_MemberHeader(actualSince, docComment)
    }

    private fun docComment(): DocComment? {
        return if (since == null && comment == null) null else {
            val c = if (comment == null) DocComment.EMPTY else parseComment(comment)
            if (since == null) c else {
                val b = DocCommentBuilder()
                b.description(c.description)
                for ((tag, items) in c.tags) {
                    for (item in items) b.tag(tag, item)
                }
                b.tag(DocCommentTag.SINCE, DocCommentItem(null, since.str()))
                b.build()
            }
        }
    }

    private fun parseComment(text: String): DocComment {
        val text2 = fixIndent(text)
        return DocCommentParser.parse(text2)
    }

    private fun fixIndent(text: String): String {
        val lines = text.lines()
        val subLines = lines.dropWhile { it.isBlank() }
        if (subLines.isEmpty()) {
            return ""
        }

        val line0 = subLines[0]
        val indentLen = getIndentLen(line0)

        val resLines = mutableListOf<String>()
        for (line in subLines) {
            val resLine = removeIndent(line, line0, indentLen)
            resLines.add(resLine)
        }

        val resText = resLines.joinToString("\n")
        return resText
    }

    private fun getIndentLen(line: String): Int {
        val n = line.length
        var i = 0
        while (i < n && line[i].isWhitespace()) ++i
        return i
    }

    private fun removeIndent(line: String, line0: String, indentLen: Int): String {
        val n = line.length
        var i = 0
        while (i < indentLen && i < n && line[i] == line0[i]) ++i
        return line.substring(i)
    }

    companion object {
        val NULL = Ld_MemberHeader(null, null)

        fun make(base: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit): Ld_MemberHeader {
            val builder = Ld_MemberHeaderBuilder(base)
            val dsl = Ld_MemberDslImpl(builder)
            block(dsl)
            return builder.buildMemberHeader()
        }

        fun make(since: String?, comment: String?): Ld_MemberHeader {
            val rSince = if (since == null) null else parseSince(since)
            return Ld_MemberHeader(rSince, comment)
        }

        fun parseSince(version: String): R_LangVersion {
            val rVersion = try {
                R_LangVersion.of(version)
            } catch (e: IllegalArgumentException) {
                throw Ld_Exception("version:invalid:$version", "Invalid version value: '$version'")
            }
            Ld_Exception.check(rVersion in RellVersions.SUPPORTED_VERSIONS) {
                "version:unknown:$rVersion" to "Unknown Rell version: $rVersion"
            }
            return rVersion
        }
    }
}

class Ld_MemberDslImpl(private val maker: Ld_MemberHeaderMaker): Ld_MemberDsl {
    override fun since(version: String) {
        maker.since(version)
    }

    override fun comment(text: String) {
        maker.comment(text)
    }
}

interface Ld_MemberHeaderMaker {
    fun since(version: String)
    fun comment(text: String)
}

open class Ld_MemberHeaderBuilder(header: Ld_MemberHeader? = null): Ld_MemberHeaderMaker {
    private var since: R_LangVersion? = null
    private var comment: String? = null

    init {
        if (header != null) {
            header(header)
        }
    }

    fun header(header: Ld_MemberHeader) {
        if (header.since != null) {
            since0(header.since)
        }
        if (header.comment != null) {
            comment(header.comment)
        }
    }

    override fun since(version: String) {
        val rVersion = Ld_MemberHeader.parseSince(version)
        since0(rVersion)
    }

    private fun since0(version: R_LangVersion) {
        val oldVer = this.since
        Ld_Exception.check(oldVer == null) { "since:already_set:$oldVer" to "since already set: $oldVer" }
        this.since = version
    }

    override fun comment(text: String) {
        Ld_Exception.check(this.comment == null) { "comment:already_set" to "comment already set" }
        this.comment = text
    }

    fun buildMemberHeader(): Ld_MemberHeader {
        return Ld_MemberHeader(since, comment)
    }
}
