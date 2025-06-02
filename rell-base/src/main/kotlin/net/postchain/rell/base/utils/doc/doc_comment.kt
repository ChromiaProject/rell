/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.utils.*
import java.util.regex.Pattern

class DocComment(
    val description: String,
    val tags: ImmMap<DocCommentTag, ImmList<DocCommentItem>>,
) {
    private val itemMap: ImmMap<ItemKey, ImmList<DocCommentItem>> by lazy {
        tags
            .flatMap { tag ->
                tag.value.mapToImmList { item -> ItemKey(tag.key, item.key) to item }
            }
            .toImmMultimap()
            .asMap()
            .mapValuesToImmMap { it.value.toImmList() }
    }

    init {
        for ((tag, items) in tags) {
            require(items.isNotEmpty()) { "Empty items: $tag" }
            for (item in items) {
                if (tag.hasKey) {
                    require(item.key != null) { "Item without a key: $tag" }
                } else {
                    require(item.key == null) { "Item with a key: $tag" }
                }
            }
        }
    }

    fun strCode(): String {
        val parts = mutableListOf(description)
        for ((tag, items) in tags) {
            val s = items.joinToString(";") { if (it.key == null) it.text else "${it.key}=${it.text}" }
            parts.add("${tag.code}:$s")
        }
        return parts.joinToString("|")
    }

    fun getItems(tag: DocCommentTag, key: String?): ImmList<DocCommentItem> {
        val itemKey = ItemKey(tag, key)
        return itemMap[itemKey].orEmpty()
    }

    override fun toString() = strCode()

    private data class ItemKey(val tag: DocCommentTag, val key: String?)

    companion object {
        val EMPTY = DocComment("", immMapOf())
    }
}

sealed class DocCommentTag(
    val code: String,
) {
    abstract val title: String
    abstract val hasKey: Boolean
    abstract val multi: Boolean

    final override fun toString() = "@$code"

    abstract fun isAllowedForSymbol(kind: DocSymbolKind): Boolean

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {
        val AUTHOR = predef("author", "Author", multi = true) { it.author }
        val PARAM = predef("param", "Parameters", hasKey = true) { it.param }
        val RETURN = predef("return", "Returns") { it.returns }
        val THROWS = predef("throws", "Throws", multi = true) { it.throws }
        val SEE = predef("see", "See Also", multi = true)
        val SINCE = predef("since", "Since")

        val ALL: ImmList<DocCommentTag> = immListOf(AUTHOR, PARAM, RETURN, THROWS, SEE, SINCE)

        private fun predef(
            code: String,
            title: String,
            hasKey: Boolean = false,
            multi: Boolean = false,
            supportChecker: (DocSymbolKind.SupportedCommentTags) -> Boolean = { true },
        ): DocCommentTag {
            return DocCommentTag_Predef(code, title, hasKey, multi, supportChecker)
        }

        fun custom(code: String): DocCommentTag = DocCommentTag_Custom(code)
    }
}

// Not overriding equals() and hashCode() - using identity equality.
private class DocCommentTag_Predef(
    code: String,
    override val title: String,
    override val hasKey: Boolean,
    override val multi: Boolean,
    private val supportChecker: (DocSymbolKind.SupportedCommentTags) -> Boolean,
): DocCommentTag(code) {
    override fun isAllowedForSymbol(kind: DocSymbolKind) = supportChecker(kind.supportedTags)
}

private class DocCommentTag_Custom(
    code: String,
): DocCommentTag(code) {
    override val title: String = "@$code"
    override val hasKey: Boolean = false
    override val multi: Boolean = true

    override fun isAllowedForSymbol(kind: DocSymbolKind) = true

    override fun equals(other: Any?) = other is DocCommentTag_Custom && code == other.code
    override fun hashCode() = code.hashCode()
}

abstract class DocCommentPos {
    private data object DocCommentPos_None: DocCommentPos() {
        override fun toString() = "n/a"
    }

    companion object {
        val NONE: DocCommentPos = DocCommentPos_None
    }
}

class DocCommentItem(
    val key: String?,
    val text: String,
    val codePos: DocCommentPos,
    val keyPos: DocCommentPos?,
)

fun interface DocCommentErrorTracker {
    fun error(pos: DocCommentPos, code: String, msg: String)

    private class DocCommentErrorTracker_Throwing(private val exFactory: (String, String) -> RuntimeException): DocCommentErrorTracker {
        override fun error(pos: DocCommentPos, code: String, msg: String) {
            throw exFactory(code, msg)
        }
    }

    companion object {
        fun throwing(exFactory: (String, String) -> RuntimeException): DocCommentErrorTracker {
            return DocCommentErrorTracker_Throwing(exFactory)
        }
    }
}

object DocCommentParser {
    private val TAG_PATTERN = Pattern.compile("^\\s*(?:[*] )?@([A-Za-z0-9_]+)(?=\\s|$)", Pattern.MULTILINE)
    private val KEY_PATTERN = Pattern.compile("^\\s*(\\S+)(\\s|$)")

    private val BUILTIN_TAGS: ImmMap<String, DocCommentTag> = DocCommentTag.ALL.associateByToImmMap { it.code }

    fun parse(
        text: String,
        kind: DocSymbolKind,
        errorTracker: DocCommentErrorTracker = DocException.ERROR_TRACKER,
        posGetter: (Int) -> DocCommentPos = { DocCommentPos.NONE },
    ): DocComment {
        val m = TAG_PATTERN.matcher(text)
        val b = DocCommentBuilder(errorTracker)

        var hasNextTag = m.find()

        val rawDescription = if (hasNextTag) text.substring(0, m.start()) else text
        val description = cleanupCommentText(rawDescription)
        b.description(description)

        while (hasNextTag) {
            val code = m.group(1)
            val codeOfs = m.start(1) - 1
            val textStart = m.end()
            hasNextTag = m.find()
            val textEnd = if (hasNextTag) m.start() else text.length
            val tagText = text.substring(textStart, textEnd)
            processTag(b, kind, code, codeOfs, tagText, textStart, errorTracker, posGetter)
        }

        return b.build()
    }

    private fun cleanupCommentText(text: String): String {
        // Need to group "raw" lines (which don't start with "*"), because they have to be trim-indented together
        // (in order to allow inserting non-*-prefixed code blocks into comments).
        val groups = text.lines()
            .mapIndexed { i, line -> cleanupCommentLine(i, line) }
            .groupAdjacent { it }

        val resLines = groups
            .flatMap { (aster, list) ->
                if (aster) list else {
                    val line = list.joinToString("\n").trimIndent()
                    listOf(line)
                }
            }

        return resLines
            .dropWhile { it.isEmpty() }
            .dropLastWhile { it.isEmpty() }
            .joinToString("\n")
    }

    private fun cleanupCommentLine(i: Int, line: String): Pair<Boolean, String> {
        val trim = line.trim()
        return when {
            i == 0 -> true to trim
            trim.startsWith("* ") -> true to trim.substring(2)
            trim == "*" -> true to ""
            else -> false to line.trimEnd()
        }
    }

    private fun processTag(
        b: DocCommentBuilder,
        kind: DocSymbolKind,
        code: String,
        codeOfs: Int,
        text: String,
        textOfs: Int,
        errorTracker: DocCommentErrorTracker,
        posGetter: (Int) -> DocCommentPos,
    ) {
        val codePos = posGetter(codeOfs)

        val builtinTag = BUILTIN_TAGS[code]

        val tag = when {
            builtinTag != null -> builtinTag
            code == "returns" -> {
                val otherTag = DocCommentTag.RETURN
                val msg = "Tag @$code is deprecated, use @${otherTag.code} instead"
                errorTracker.error(codePos, "comment:tag:deprecated:$code", msg)
                otherTag
            }
            else -> {
                errorTracker.error(codePos, "comment:tag:unknown:$code", "Invalid comment tag: @$code")
                DocCommentTag.custom(code)
            }
        }

        if (!tag.isAllowedForSymbol(kind)) {
            val msg = "Comment tag @$code not allowed for ${kind.msg.nounWithArticle()}"
            errorTracker.error(codePos, "comment:tag:not_allowed:$kind:$code", msg)
            return
        }

        var key: String? = null
        var keyPos: DocCommentPos? = null
        var tagText = text

        if (tag.hasKey) {
            val m = KEY_PATTERN.matcher(tagText)
            if (m.find()) {
                key = m.group(1)
                keyPos = posGetter(textOfs + m.start(1))
                tagText = tagText.substring(m.end())
            }
        }

        tagText = cleanupCommentText(tagText)

        val item = DocCommentItem(key, tagText, codePos, keyPos)
        b.tag(tag, item)
    }
}

class DocCommentBuilder(private val errorTracker: DocCommentErrorTracker) {
    private var description: String? = null
    private val tags = mutableMultimapOf<DocCommentTag, DocCommentItem>()
    private val keys = mutableSetOf<Pair<DocCommentTag, String?>>()

    fun description(text: String) {
        require(description == null) { "Description already set" }
        description = text.trim()
    }

    fun tag(tag: DocCommentTag, item: DocCommentItem) {
        if (tag.hasKey) {
            if (item.key == null) {
                errorTracker.error(item.codePos, "tag:no_key:${tag.code}", "Tag @${tag.code} requires an argument")
                return
            }
        } else {
            require(item.key == null) // Internal error.
        }

        val exists = !keys.add(tag to item.key)
        if (tag.multi || !exists) {
            tags.put(tag, item)
        } else {
            val code = if (item.key == null) tag.code else "${tag.code}[${item.key}]"
            val msg = if (item.key == null) "@${tag.code}" else "@${tag.code} ${item.key}"
            val errPos = item.keyPos ?: item.codePos
            errorTracker.error(errPos, "comment:tag:duplicate:$code", "Duplicate tag: $msg")
        }
    }

    fun build(): DocComment {
        val resTags = mutableMapOf<DocCommentTag, ImmList<DocCommentItem>>()

        for (tag in DocCommentTag.ALL) {
            val items = tags.asMap()[tag]?.toImmList()
            if (items != null) {
                resTags[tag] = items
            }
        }

        for ((tag, items) in tags.asMap()) {
            if (tag !in resTags) {
                resTags[tag] = items.toImmList()
            }
        }

        return DocComment(description ?: "", resTags.toImmMap())
    }
}
