/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Renders one page (HTML string) from a `PageSpec`. Pages share a common shell — `<head>`
 * with embedded CSS + optional user-provided `customStyleSheets`, a sidebar containing the
 * navigation, and a `<main>` with the per-page body produced by `PageSpec.body`.
 */
internal class Pages(
    private val site: Doc_Site,
    private val markdown: Markdown,
    private val nav: Navigation,
    private val signatureRender: SignatureRender,
) {

    fun renderSitePage(spec: PageSpec): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendHTML().html {
            attributes["lang"] = "en"
            head {
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                title("${escapeHtml(spec.title)} — ${escapeHtml(site.title)}")
                style { unsafe { +SITE_CSS } }
                script { unsafe { +THEME_BOOT_JS } }
                script { unsafe { +RELL_HIGHLIGHT_JS } }
                for (sheet in site.customStyleSheets) {
                    val rel = Hrefs.relativeFrom(spec.ctx.relativePath, "styles/${sheet.fileName}")
                    link(rel = "stylesheet", href = rel)
                }
            }
            body {
                div(classes = "layout") {
                    div(classes = "sidebar") {
                        val rootHref = Hrefs.relativeFrom(spec.ctx.relativePath, "index.html")
                        div(classes = "sidebar-head") {
                            div(classes = "sidebar-head-row") {
                                a(href = rootHref, classes = "site-title") { +site.title }
                                button(classes = "theme-toggle") {
                                    attributes["type"] = "button"
                                    attributes["aria-label"] = "Toggle dark mode"
                                    attributes["title"] = "Toggle theme"
                                    unsafe { +THEME_TOGGLE_SVG }
                                }
                            }
                            span(classes = "version-pill") {
                                +"Rell ${net.postchain.rell.base.utils.RellVersions.VERSION_STR}"
                            }
                        }
                        div(classes = "sidebar-body") {
                            nav.renderInto(this, spec.ctx)
                        }
                    }
                    main {
                        renderBreadcrumbs(this, spec)
                        spec.body(this, this@Pages)
                        if (site.footerMessage.isNotBlank()) {
                            div(classes = "colophon") { span { +site.footerMessage } }
                        }
                    }
                }
            }
        }
    }

    private fun renderBreadcrumbs(out: FlowContent, spec: PageSpec) {
        out.header(classes = "doc-head") {
            div(classes = "doc-head-inner") {
                if (spec.kicker.isNotBlank()) {
                    div(classes = "doc-kicker") { +spec.kicker }
                }
                val titleClasses = if (spec.deprecated) "doc-title deprecated" else "doc-title"
                h1(classes = titleClasses) { +spec.title }
                if (spec.breadcrumbs.isNotEmpty()) {
                    div(classes = "doc-breadcrumbs") {
                        for ((i, crumb) in spec.breadcrumbs.withIndex()) {
                            if (i > 0) span(classes = "sep") { +" / " }
                            if (crumb.href != null) {
                                a(href = crumb.href) { +crumb.label }
                            } else {
                                span { +crumb.label }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Body builders for the four kinds of pages ───────────────────────────

    fun renderSiteIndexBody(out: FlowContent) {
        val ctx = PageContext(relativePath = "index.html", currentPackage = null)
        if (site.modules.isEmpty()) return
        // Single-module site (the common case for both system-lib and dapp generation): the
        // page already prints the module name as `<h1 class="doc-title">`, so a `## <module>`
        // section heading immediately below duplicates the title. Inline the module body
        // directly under one neutral heading.
        if (site.modules.size == 1) {
            val module = site.modules[0]
            if (module.docMd.isNotBlank()) {
                out.div(classes = "prose") { unsafe { +markdown.renderHtml(module.docMd, ctx) } }
            }
            val packages = module.packages.filter { it.qname !in site.hiddenPackages }
            if (packages.isNotEmpty()) {
                out.section(classes = "section") {
                    div(classes = "section-head") {
                        div(classes = "section-title") {
                            +(if (module.system) "Namespaces" else "Modules")
                        }
                    }
                    renderPackageTable(this, module, packages, ctx)
                }
            }
            return
        }
        for (module in site.modules) {
            out.section(classes = "section") {
                div(classes = "section-head") {
                    div(classes = "section-title") { +module.name }
                }
                if (module.docMd.isNotBlank()) {
                    div(classes = "prose") { unsafe { +markdown.renderHtml(module.docMd, ctx) } }
                }
                val packages = module.packages.filter { it.qname !in site.hiddenPackages }
                if (packages.isNotEmpty()) {
                    renderPackageTable(this, module, packages, ctx)
                }
            }
        }
    }

    fun renderModuleIndexBody(out: FlowContent, module: Doc_Module) {
        val ctx = PageContext(relativePath = Paths.moduleIndexPath(module), currentPackage = null)
        if (module.docMd.isNotBlank()) {
            out.div(classes = "prose") { unsafe { +markdown.renderHtml(module.docMd, ctx) } }
        }
        val packages = module.packages.filter { it.qname !in site.hiddenPackages }
        if (packages.isNotEmpty()) {
            out.section(classes = "section") {
                div(classes = "section-head") {
                    div(classes = "section-title") { +(if (module.system) "Namespaces" else "Modules") }
                }
                renderPackageTable(this, module, packages, ctx)
            }
        }
    }

    fun renderPackageBody(out: FlowContent, module: Doc_Module, pkg: Doc_Package) {
        val ctx = PageContext(
            relativePath = Paths.packageIndexPath(module, pkg),
            currentPackage = pkg,
        )
        if (pkg.docMd.isNotBlank()) {
            out.div(classes = "prose") { unsafe { +markdown.renderHtml(pkg.docMd, ctx) } }
        }
        val (classes, functions, properties, aliases) = bucketDefs(pkg.defs)
        renderDefTable(out, "Types", classes, module, pkg, ctx)
        renderDefTable(out, "Functions", functions, module, pkg, ctx)
        renderDefTable(out, "Properties", properties, module, pkg, ctx)
        renderDefTable(out, "Aliases", aliases, module, pkg, ctx)
    }

    fun renderDefPageBody(out: FlowContent, module: Doc_Module, pkg: Doc_Package, def: Doc_Def) {
        val ctx = PageContext(
            relativePath = Paths.pageRelativePath(module, pkg, def),
            currentPackage = pkg,
        )
        renderDefSignatureSection(out, def, ctx)
        if (def is Doc_Class) {
            val (nestedClasses, methods, attrs, aliases) = bucketDefs(def.members)
            if (def.kind == Doc_ClassKind.ENUM && def.entries.isNotEmpty()) {
                out.section(classes = "section") {
                    div(classes = "section-head") { div(classes = "section-title") { +"Entries" } }
                    div(classes = "def-grid") {
                        for (entry in def.entries) {
                            // Enum entries aren't separately documented, so the card is non-clickable
                            // — `div`, not `a` — and only carries the name.
                            div(classes = "def-card def-card-static") {
                                div(classes = "def-card-name") { +entry }
                            }
                        }
                    }
                }
            }
            renderDefTable(out, "Attributes", attrs, module, pkg, ctx, parentClass = def)
            renderDefTable(out, "Methods", methods, module, pkg, ctx, parentClass = def)
            renderDefTable(out, "Types", nestedClasses, module, pkg, ctx, parentClass = def)
            renderDefTable(out, "Aliases", aliases, module, pkg, ctx, parentClass = def)
        }
    }

    fun renderMemberPageBody(
        out: FlowContent,
        module: Doc_Module,
        pkg: Doc_Package,
        owner: Doc_Class,
        member: Doc_Def,
    ) {
        val ctx = PageContext(
            relativePath = Paths.memberRelativePath(module, pkg, owner, member),
            currentPackage = pkg,
        )
        renderDefSignatureSection(out, member, ctx)
    }

    /**
     * Render the head of a def page (or class-member page): the signature, source link, deprecation
     * note, and doc body. For an overloaded `Doc_Function` (`overloads` is non-empty), emit one
     * block per overload — each block carries its own signature, source link, deprecation note,
     * and doc text — separated by a horizontal rule, matching the layout Kotlin Dokka uses.
     */
    private fun renderDefSignatureSection(out: FlowContent, def: Doc_Def, ctx: PageContext) {
        if (def is Doc_Function && def.overloads.isNotEmpty()) {
            val all = listOf(def) + def.overloads
            for ((i, overload) in all.withIndex()) {
                if (i > 0) out.hr(classes = "overload-sep")
                renderSignature(out, overload)
                renderSourceLink(out, overload.source)
                renderDeprecated(out, overload)
                if (overload.docMd.isNotBlank()) {
                    out.div(classes = "prose") { unsafe { +markdown.renderHtml(overload.docMd, ctx) } }
                }
            }
            return
        }
        renderSignature(out, def)
        renderSourceLink(out, def.source)
        renderDeprecated(out, def)
        if (def.docMd.isNotBlank()) {
            out.div(classes = "prose") { unsafe { +markdown.renderHtml(def.docMd, ctx) } }
        }
    }

    // ─── Shared bits ─────────────────────────────────────────────────────────

    private fun renderPackageTable(
        out: FlowContent,
        module: Doc_Module,
        packages: List<Doc_Package>,
        ctx: PageContext,
    ) {
        // Root package goes first — every top-level type / function the user reaches for
        // (`integer`, `text`, `print()`, …) lives there, so it deserves the top spot. Other
        // packages keep their build-time order (which matches the lib-author's `include`
        // sequence and is close to alphabetical for our libs).
        val sortedPkgs = packages.sortedBy { if (it.qname.isEmpty()) 0 else 1 }
        out.div(classes = "def-grid") {
            for (pkg in sortedPkgs) {
                val href = Hrefs.relativeFrom(ctx.relativePath, Paths.packageIndexPath(module, pkg))
                a(href = href, classes = "def-card") {
                    attributes["anchor-label"] = pkg.qname
                    div(classes = "def-card-name") { +pkg.displayName }
                    val summary = markdown.renderSummaryText(pkg.docMd)
                    if (summary.isNotBlank()) {
                        div(classes = "def-card-summary") { unsafe { +summary } }
                    }
                }
            }
        }
    }

    private fun renderDefTable(
        out: FlowContent,
        title: String,
        defs: List<Doc_Def>,
        module: Doc_Module,
        pkg: Doc_Package,
        ctx: PageContext,
        parentClass: Doc_Class? = null,
    ) {
        if (defs.isEmpty()) return
        out.section(classes = "section") {
            div(classes = "section-head") { div(classes = "section-title") { +title } }
            div(classes = "def-grid") {
                for (def in defs) {
                    val deprecated = def.deprecated != null
                    val pagePath = if (parentClass != null) {
                        Paths.memberRelativePath(module, pkg, parentClass, def)
                    } else {
                        Paths.pageRelativePath(module, pkg, def)
                    }
                    val href = Hrefs.relativeFrom(ctx.relativePath, pagePath)
                    val classes = if (deprecated) "def-card deprecated" else "def-card"
                    a(href = href, classes = classes) {
                        attributes["anchor-label"] = def.qname
                        div(classes = "def-card-name") { +def.name }
                        val summary = markdown.renderSummaryText(def.docMd)
                        if (summary.isNotBlank()) {
                            div(classes = "def-card-summary") { unsafe { +summary } }
                        }
                    }
                }
            }
        }
    }

    private fun renderSignature(out: FlowContent, def: Doc_Def) {
        val html = signatureRender.render(def)
        out.div(classes = "signature") {
            code { unsafe { +html } }
        }
    }

    private fun renderSourceLink(out: FlowContent, source: Doc_Source?) {
        if (source == null) return
        val resolved = resolveSourceLink(source, site.sourceLinks) ?: return
        out.div(classes = "source-link symbol") {
            span(classes = "floating-right") { a(href = resolved) { +"source" } }
        }
    }

    private fun renderDeprecated(out: FlowContent, def: Doc_Def) {
        val d = def.deprecated ?: return
        // Inline note — no callout. Strikethrough on the page title and listings carries the
        // visual cue; this paragraph is the textual reason a reader looking at this page should
        // not use the def. The "Deprecated" label stays upright — striking it through hurts
        // readability and the surrounding muted styling already conveys the state.
        out.p(classes = "deprecated-note") {
            strong { +"Deprecated" }
            if (d.forRemoval) +" (for removal)"
            +": "
            +d.message
        }
    }

    private data class Bucketed(
        val classes: List<Doc_Def>,
        val functions: List<Doc_Def>,
        val properties: List<Doc_Def>,
        val aliases: List<Doc_Def>,
    )

    private fun bucketDefs(defs: List<Doc_Def>): Bucketed {
        val classes = mutableListOf<Doc_Def>()
        val functions = mutableListOf<Doc_Def>()
        val properties = mutableListOf<Doc_Def>()
        val aliases = mutableListOf<Doc_Def>()
        for (def in defs) when (def) {
            is Doc_Class -> classes.add(def)
            is Doc_Function -> functions.add(def)
            is Doc_Property -> properties.add(def)
            is Doc_TypeAlias -> aliases.add(def)
        }
        return Bucketed(classes.sortedBy { it.name }, functions.sortedBy { it.name }, properties, aliases.sortedBy { it.name })
    }
}

/**
 * Build the source-link URL from a `Doc_Source` plus the configured source-link prefix list.
 *
 * The Rell compiler reports `R_Definition.docSourcePos.path` *relative* to the source root it
 * walked. In the typical case (configured local-dir == project source root) we just append that
 * relative path to the remote URL verbatim. The absolute-path branch is kept as a fallback in
 * case the caller passes already-resolved paths.
 *
 * The first link that matches wins; lookups are O(n) — only a handful of prefixes per site.
 */
internal fun resolveSourceLink(source: Doc_Source, links: List<Doc_SourceLink>): String? {
    if (links.isEmpty()) return null
    val sourcePath = Path(source.path)
    for (link in links) {
        val relative = relativizeAgainst(sourcePath, link.localDirectory) ?: continue
        val urlBase = link.remoteUrl.toString().trimEnd('/')
        return buildString {
            append(urlBase).append('/').append(relative)
            if (source.line != null && !link.remoteLineSuffix.isNullOrEmpty()) {
                append(link.remoteLineSuffix).append(source.line)
            }
        }
    }
    return null
}

private fun relativizeAgainst(source: Path, base: Path): String? {
    // Common case: compiler reports source paths relative to the source root. We trust the
    // caller to have set `localDirectory` to that same root and pass the relative path through.
    if (!source.isAbsolute) return source.toString().replace('\\', '/')
    val rel = runCatching { base.toAbsolutePath().relativize(source.toAbsolutePath()) }.getOrNull() ?: return null
    val text = rel.toString()
    if (text.startsWith("..")) return null
    return text.replace('\\', '/')
}

/**
 * Declarative shell for one rendered page: where it lives, what its `<title>` is, the
 * breadcrumb trail above the title, and the body builder (`(receiver, pages)`).
 */
internal class PageSpec(
    val ctx: PageContext,
    val title: String,
    val kicker: String,
    val breadcrumbs: List<Crumb>,
    /** When true the page title renders with strikethrough — used for deprecated defs. */
    val deprecated: Boolean = false,
    val body: (FlowContent, Pages) -> Unit,
) {
    data class Crumb(val label: String, val href: String?)
}

