/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.Doc_Package
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Markdown rendering with cross-reference resolution.
 *
 * The doc-comment convention is to write `&#91;some.qualified.name&#93;` (shortcut reference link with
 * no inline target) to link to a Rell symbol. CommonMark by default parses these as literal text
 * because no matching `&#91;name&#93;: url` definition exists. We post-process the AST to:
 *  - find every `Text` node containing `[<qname>]` patterns,
 *  - replace them with `Link` nodes pointing at the resolved URL,
 *  - skip text inside code spans/blocks (commonmark already isolates those into different node
 *    types, so the AbstractVisitor naturally walks past them).
 */
internal class Markdown(private val index: SiteIndex) {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), AutolinkExtension.create()))
        .build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    fun renderHtml(markdown: String, currentPage: PageContext): String {
        if (markdown.isBlank()) return ""
        val root = parser.parse(markdown)
        ResolveRefsVisitor(index, currentPage).resolveAll(root)
        return renderer.render(root)
    }

    /**
     * Strip markdown to a single inline-text line — used for navigation summaries. Summaries
     * cover only the *description* portion of a doc comment; tag sections (`@since`, `@param`,
     * `@return`, …) never bleed into the summary, because their content out of context
     * (a lone version number, an orphan parameter name) is worse than no summary at all.
     */
    fun renderSummaryText(markdown: String): String {
        if (markdown.isBlank()) return ""
        // Walk paragraphs (blocks separated by blank lines). The first paragraph that *is* a
        // bold-wrapped heading marks the start of tag sections (`DocSymbolText.appendSectionHeader`
        // writes `**Since**`, `**Parameters**`, etc.) — stop there: everything beyond belongs to
        // a tag, not the description.
        for (paragraph in markdown.split(Regex("\n\\s*\n"))) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue
            if (HEADING_ONLY.matches(trimmed)) return ""
            val firstLine = trimmed.lineSequence().first { it.isNotBlank() }
            return firstLine
                .replace(Regex("""\[([^]]+)]\([^)]*\)"""), "$1")
                .replace(Regex("""\[([^]]+)]"""), "$1")
                .replace(Regex("[`*_]"), "")
                .trim()
        }
        return ""
    }

    private companion object {
        // Paragraph is purely a bold heading: `**Word**` or `**Two words**` with no body text.
        private val HEADING_ONLY = Regex("""\*\*[^*]+\*\*""")
    }

    private class ResolveRefsVisitor(
        private val index: SiteIndex,
        private val ctx: PageContext,
    ) : AbstractVisitor() {

        fun resolveAll(root: Node) {
            root.accept(this)
        }

        override fun visit(text: Text) {
            // Don't touch text inside code spans; commonmark uses Code/FencedCodeBlock/IndentedCodeBlock
            // for those, so we'd never see them here. We still skip if our text is the literal child
            // of a Link (already-formed link's label).
            if (text.parent is Link) {
                super.visit(text)
                return
            }
            replaceText(text)
        }

        override fun visit(code: Code) = Unit
        override fun visit(block: FencedCodeBlock) = Unit
        override fun visit(block: IndentedCodeBlock) = Unit

        private fun replaceText(text: Text) {
            val literal = text.literal ?: return
            val matches = REF_PATTERN.findAll(literal).toList()
            if (matches.isEmpty()) return
            if (matches.none { canResolve(it.groupValues[1]) }) return

            var prev = 0
            val parent = text.parent ?: return
            val anchor = text  // reference point for `insertBefore`
            for (m in matches) {
                if (!canResolve(m.groupValues[1])) continue
                if (m.range.first > prev) {
                    anchor.insertBefore(Text(literal.substring(prev, m.range.first)))
                }
                val name = m.groupValues[1]
                val href = resolveHref(name)
                if (href != null) {
                    val link = Link(href, null)
                    link.appendChild(Text(name))
                    anchor.insertBefore(link)
                } else {
                    anchor.insertBefore(Text(m.value))
                }
                prev = m.range.last + 1
            }
            if (prev > 0) {
                if (prev < literal.length) anchor.insertBefore(Text(literal.substring(prev)))
                text.unlink()
                // `parent` reference kept above so we don't accidentally hold a detached node.
                @Suppress("UNUSED_EXPRESSION") parent
            }
        }

        private fun canResolve(name: String): Boolean = index.resolveAny(name, ctx.currentPackage) != null

        private fun resolveHref(name: String): String? {
            val entry = index.resolveAny(name, ctx.currentPackage) ?: return null
            return Hrefs.relativeFrom(ctx.relativePath, entry.href())
        }

        companion object {
            // Match `[a.b.c]` where a/b/c are Rell identifiers. Excludes patterns followed by `(`
            // (already-formed inline links) or `:` (reference-link definitions).
            private val REF_PATTERN = Regex("""\[([A-Za-z_][A-Za-z_0-9.]*)](?![(\[:])""")
        }
    }
}

/** Where a page lives — used to compute relative hrefs from one page to another. */
internal data class PageContext(
    val relativePath: String,
    val currentPackage: Doc_Package?,
)
