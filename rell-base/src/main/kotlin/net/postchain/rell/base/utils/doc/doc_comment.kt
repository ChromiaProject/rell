/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.utils.*
import java.util.regex.Pattern

class DocComment(
    val description: String,
    val tags: Map<DocCommentTag, List<DocCommentItem>>,
) {
    private val itemMap: Map<ItemKey, List<DocCommentItem>> by lazy {
        tags
            .flatMap { tag ->
                tag.value.map { item -> ItemKey(tag.key, item.key) to item }
            }
            .toImmMultimap()
            .asMap()
            .mapValues { it.value.toImmList() }
            .toImmMap()
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

    fun getItems(tag: DocCommentTag, key: String?): List<DocCommentItem> {
        val itemKey = ItemKey(tag, key)
        return itemMap[itemKey] ?: immListOf()
    }

    override fun toString() = strCode()

    private data class ItemKey(val tag: DocCommentTag, val key: String?)

    companion object {
        val EMPTY = DocComment("", immMapOf())
    }
}

data class DocCommentTag(val code: String, val title: String, val hasKey: Boolean = false, val multi: Boolean = false) {
    @Suppress("MemberVisibilityCanBePrivate")
    companion object {
        val PARAM = DocCommentTag("param", "Parameters", hasKey = true)
        val RETURNS = DocCommentTag("returns", "Returns")
        val SINCE = DocCommentTag("since", "Since")
        val SEE = DocCommentTag("see", "See Also", multi = true)

        val ALL: List<DocCommentTag> = immListOf(PARAM, RETURNS, SINCE, SEE)
    }
}

class DocCommentItem(val key: String?, val text: String)

object DocCommentParser {
    private val TAG_PATTERN = Pattern.compile("^@([A-Za-z0-9_]+)(\\s+|$)", Pattern.MULTILINE)

    private val BUILTIN_TAGS: Map<String, DocCommentTag> = DocCommentTag.ALL.associateBy { it.code }.toImmMap()

    fun parse(
        text: String,
        errorTracker: ErrorTracker = DocException.ERROR_TRACKER,
    ): DocComment {
        val m = TAG_PATTERN.matcher(text)
        val b = DocCommentBuilder(errorTracker)

        var hasNextTag = m.find()

        val description = if (hasNextTag) text.substring(0, m.start()).trim() else text.trim()
        b.description(description)

        while (hasNextTag) {
            val code = m.group(1)
            val textStart = m.end()
            hasNextTag = m.find()
            val textEnd = if (hasNextTag) m.start() else text.length
            val tagText = text.substring(textStart, textEnd).trim()
            processTag(b, code, tagText, errorTracker)
        }

        return b.build()
    }

    private fun processTag(b: DocCommentBuilder, code: String, text: String, errorTracker: ErrorTracker) {
        val builtinTag = BUILTIN_TAGS[code]
        val tag = when {
            builtinTag != null -> builtinTag
            else -> {
                errorTracker.error("comment:tag:unknown:$code", "Invalid comment tag: @$code")
                DocCommentTag(code, "@$code", multi = true)
            }
        }

        var key: String? = null
        var tagText = text

        if (tag.hasKey) {
            var i = tagText.indexOfFirst { it.isWhitespace() }
            if (i < 0) i = tagText.length
            val key0 = tagText.substring(0, i).trim()
            if (key0.isNotEmpty()) {
                key = key0
                tagText = tagText.substring(i).trim()
            }
        }

        b.tag(tag, DocCommentItem(key, tagText))
    }
}

class DocCommentBuilder(
    private val errorTracker: ErrorTracker,
) {
    private var description: String? = null
    private val tags = mutableMultimapOf<DocCommentTag, DocCommentItem>()
    private val keys = mutableSetOf<Pair<DocCommentTag, String?>>()

    fun description(text: String) {
        require(description == null) { "Description already set" }
        description = text.trim()
    }

    fun tag(tag: DocCommentTag, item: DocCommentItem) {
        if (tag.hasKey) {
            DocException.check(item.key != null) {
                "tag:no_key:${tag.code}" to "Tag @${tag.code} requires an argument"
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
            errorTracker.error("comment:tag:duplicate:$code", "Duplicate tag: $msg")
        }
    }

    fun build(): DocComment {
        val resTags = mutableMapOf<DocCommentTag, List<DocCommentItem>>()

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
