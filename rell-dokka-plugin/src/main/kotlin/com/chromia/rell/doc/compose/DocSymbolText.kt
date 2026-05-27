/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.compose

import com.chromia.rell.doc.model.Doc_Deprecated
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocCommentItem
import net.postchain.rell.base.utils.doc.DocCommentTag
import net.postchain.rell.base.utils.doc.DocSymbol
import java.util.*

/**
 * Renders a Rell `DocSymbol` (description + tag bag) into a single markdown blob.
 *
 * Tag handling: `@param name desc` becomes a bullet list under "Parameters"; `@return`,
 * `@throws`, `@see`, `@since`, `@author` each get their own headed section. The renderer feeds
 * the resulting text through commonmark, so it doesn't need to know about Rell-specific tags.
 *
 * `extraSuffix` is appended after the tag sections — used for alias breadcrumbs ("Alias of
 * `\[target]`-style link) and similar synthetic content that ought to read like another tag.
 */
internal fun DocSymbol.markdown(extraSuffix: String? = null): String {
    val body = comment?.toMarkdown() ?: ""
    return joinMarkdown(body, extraSuffix?.trim()?.takeIf { it.isNotEmpty() })
}

internal fun DocComment.toMarkdown(): String = buildString {
    if (description.isNotBlank()) append(description.trim())
    appendTagsTo(this, tags)
}.trim()

private fun appendTagsTo(out: StringBuilder, tagMap: Map<DocCommentTag, List<DocCommentItem>>) {
    val paramItems = tagMap[DocCommentTag.PARAM]
    if (!paramItems.isNullOrEmpty()) {
        out.appendSectionHeader(DocCommentTag.PARAM.title)
        paramItems.forEach { item ->
            out.append("- **").append(item.key ?: "?").append("** — ")
                .append(item.text.singleLine()).append('\n')
        }
    }

    fun simpleSection(tag: DocCommentTag, format: (DocCommentItem) -> String) {
        val items = tagMap[tag] ?: return
        // Skip the section entirely when every item formats to blank text. Some stdlib decls
        // carry a `@since` (or similar) tag with no value — emitting a lone `**Since**` heading
        // shows up as garbage in the package-index summary cell.
        val formatted = items.map(format).filter { it.isNotBlank() }
        if (formatted.isEmpty()) return
        out.appendSectionHeader(tag.title)
        formatted.forEach { out.append(it).append('\n') }
    }

    simpleSection(DocCommentTag.RETURN) { it.text.trim() }
    simpleSection(DocCommentTag.THROWS) { formatThrowsLine(it) }
    simpleSection(DocCommentTag.SEE) { formatSeeLine(it) }
    simpleSection(DocCommentTag.SINCE) { it.text.trim() }
    simpleSection(DocCommentTag.AUTHOR) { it.text.trim() }
}

private fun StringBuilder.appendSectionHeader(title: String) {
    if (isNotEmpty()) append("\n\n")
    append("**").append(title).append("**\n\n")
}

private fun formatSeeLine(item: DocCommentItem): String {
    val (head, rest) = item.text.splitFirstWord()
    return if (rest.isNullOrBlank()) "- [$head]" else "- [$head] — ${rest.singleLine()}"
}

private fun formatThrowsLine(item: DocCommentItem): String {
    val (head, rest) = item.text.splitFirstWord()
    return if (rest.isNullOrBlank()) "- `$head`" else "- `$head` — ${rest.singleLine()}"
}

private fun String.splitFirstWord(): Pair<String, String?> {
    val trim = trim()
    val space = trim.indexOf(' ')
    return if (space < 0) trim to null else trim.substring(0, space) to trim.substring(space + 1).trim()
}

private fun String.singleLine(): String = lines().joinToString(" ") { it.trim() }.trim()

private fun joinMarkdown(body: String, suffix: String?): String = when {
    suffix == null -> body
    body.isEmpty() -> suffix
    else -> "$body\n\n$suffix"
}

internal fun C_Deprecated.toDocDeprecated(): Doc_Deprecated {
    val raw = detailsMessage()
    val msg = (if (raw.length > 2) raw.substring(2) else raw).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
    }
    return Doc_Deprecated(message = msg, forRemoval = error)
}
