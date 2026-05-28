/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.Doc_Class
import com.chromia.rell.doc.model.Doc_Site
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Top-level orchestrator: takes a `Doc_Site` and writes the full directory tree at
 * `outputDir`. Responsible for the file layout, asset copying, and emission of `index.html`,
 * `navigation.html`, `scripts/pages.json`, and `styles/site.css`.
 *
 * The directory shape matches what `chromia-cli`'s integration tests expect:
 *  - `<outputDir>/index.html` — site root
 *  - `<outputDir>/<moduleSlug>/index.html` — per-module index
 *  - `<outputDir>/<moduleSlug>/<package>/index.html` — per-package index
 *  - `<outputDir>/<moduleSlug>/<package>/<def>.html` — top-level def
 *  - `<outputDir>/<moduleSlug>/<package>/<Type>/index.html` — class-like def
 *  - `<outputDir>/<moduleSlug>/<package>/<Type>/<member>.html` — class member
 *  - `<outputDir>/styles/<userSheet>.css`, `<outputDir>/images/<userAsset>`
 */
internal class SiteRender(private val outputDir: Path) {

    fun render(site: Doc_Site) {
        outputDir.createDirectories()

        val index = SiteIndex.build(site)
        val markdown = Markdown(index)
        val navigation = Navigation(site)

        writeSiteRoot(site, index, markdown, navigation)
        copyUserAssets(site)
        writeStaticAssets()
        writeSearchIndex(site)
    }

    private fun writeSiteRoot(
        site: Doc_Site,
        index: SiteIndex,
        markdown: Markdown,
        navigation: Navigation,
    ) {
        val typeRender = TypeRender(index, PageContext("index.html", null))
        val signature = SignatureRender(typeRender)
        val pages = Pages(site, markdown, navigation, signature)

        writePage(
            relativePath = "index.html",
            html = pages.renderSitePage(
                PageSpec(
                    ctx = PageContext("index.html", null),
                    title = site.title,
                    kicker = if (site.system) "API REFERENCE" else "DOCUMENTATION",
                    breadcrumbs = emptyList(),
                    body = { out, p -> p.renderSiteIndexBody(out) },
                ),
            ),
        )

        writePage(
            relativePath = "navigation.html",
            html = navigation.renderStandalone(),
        )

        for (module in site.modules) {
            writeModule(site, index, markdown, navigation, module)
        }
    }

    private fun writeModule(
        site: Doc_Site,
        index: SiteIndex,
        markdown: Markdown,
        navigation: Navigation,
        module: com.chromia.rell.doc.model.Doc_Module,
    ) {
        val moduleIndexPath = Paths.moduleIndexPath(module)
        renderPage(
            site, index, markdown, navigation,
            relativePath = moduleIndexPath,
            spec = PageSpec(
                ctx = PageContext(moduleIndexPath, null),
                title = module.name,
                kicker = if (module.system) "SYSTEM LIBRARY" else "MODULE",
                breadcrumbs = listOf(
                    PageSpec.Crumb(site.title, Hrefs.relativeFrom(moduleIndexPath, "index.html")),
                ),
                body = { out, p -> p.renderModuleIndexBody(out, module) },
            ),
        )

        for (pkg in module.packages) {
            writePackage(site, index, markdown, navigation, module, pkg)
        }
    }

    private fun writePackage(
        site: Doc_Site,
        index: SiteIndex,
        markdown: Markdown,
        navigation: Navigation,
        module: com.chromia.rell.doc.model.Doc_Module,
        pkg: com.chromia.rell.doc.model.Doc_Package,
    ) {
        val packageIndex = Paths.packageIndexPath(module, pkg)
        renderPage(
            site, index, markdown, navigation,
            relativePath = packageIndex,
            spec = PageSpec(
                ctx = PageContext(packageIndex, pkg),
                title = pkg.displayName,
                kicker = "PACKAGE",
                breadcrumbs = listOf(
                    PageSpec.Crumb(site.title, Hrefs.relativeFrom(packageIndex, "index.html")),
                    PageSpec.Crumb(module.name, Hrefs.relativeFrom(packageIndex, Paths.moduleIndexPath(module))),
                ),
                body = { out, p -> p.renderPackageBody(out, module, pkg) },
            ),
        )

        for (def in pkg.defs) {
            val pageRel = Paths.pageRelativePath(module, pkg, def)
            renderPage(
                site, index, markdown, navigation,
                relativePath = pageRel,
                spec = PageSpec(
                    ctx = PageContext(pageRel, pkg),
                    title = def.name,
                    kicker = defKicker(def),
                    breadcrumbs = listOf(
                        PageSpec.Crumb(site.title, Hrefs.relativeFrom(pageRel, "index.html")),
                        PageSpec.Crumb(module.name, Hrefs.relativeFrom(pageRel, Paths.moduleIndexPath(module))),
                        PageSpec.Crumb(pkg.displayName, Hrefs.relativeFrom(pageRel, Paths.packageIndexPath(module, pkg))),
                    ),
                    deprecated = isWhollyDeprecated(def),
                    body = { out, p -> p.renderDefPageBody(out, module, pkg, def) },
                ),
            )

            // Type aliases were `<Name>/index.html` under legacy Dokka but are flat `<Name>.html`
            // now — emit the legacy path as a redirect so existing links keep working.
            if (def is com.chromia.rell.doc.model.Doc_TypeAlias) {
                val flatLeaf = Paths.pageRelativePath(module, pkg, def).substringAfterLast('/')
                writePage(Paths.classifierIndexPath(module, pkg, def), redirectHtml("../$flatLeaf"))
            }

            if (def is Doc_Class) {
                for (member in def.members) {
                    val memberRel = Paths.memberRelativePath(module, pkg, def, member)
                    // Attributes don't get their own page — they're embedded on the owner's page.
                    // But the legacy Dokka URL (`…/<Owner>/<attr>.html`) is kept alive as a redirect
                    // to the owner's anchor, so existing links/bookmarks still resolve.
                    if (member is com.chromia.rell.doc.model.Doc_Property) {
                        writePage(memberRel, redirectHtml("index.html#${Paths.memberAnchor(member)}"))
                        continue
                    }
                    renderPage(
                        site, index, markdown, navigation,
                        relativePath = memberRel,
                        spec = PageSpec(
                            ctx = PageContext(memberRel, pkg),
                            title = "${def.name}.${member.name}",
                            kicker = defKicker(member),
                            breadcrumbs = listOf(
                                PageSpec.Crumb(site.title, Hrefs.relativeFrom(memberRel, "index.html")),
                                PageSpec.Crumb(module.name, Hrefs.relativeFrom(memberRel, Paths.moduleIndexPath(module))),
                                PageSpec.Crumb(pkg.displayName, Hrefs.relativeFrom(memberRel, Paths.packageIndexPath(module, pkg))),
                                PageSpec.Crumb(def.name, Hrefs.relativeFrom(memberRel, Paths.pageRelativePath(module, pkg, def))),
                            ),
                            deprecated = isWhollyDeprecated(member),
                            body = { out, p -> p.renderMemberPageBody(out, module, pkg, def, member) },
                        ),
                    )
                }
            }
        }
    }

    /**
     * Page-title strikethrough rule: for an overloaded function, strike only when every signature
     * is deprecated — otherwise at least one supported overload still exists and a struck title
     * misleads the reader.
     */
    private fun isWhollyDeprecated(def: com.chromia.rell.doc.model.Doc_Def): Boolean {
        if (def.deprecated == null) return false
        if (def is com.chromia.rell.doc.model.Doc_Function) {
            return def.overloads.all { it.deprecated != null }
        }
        return true
    }

    private fun defKicker(def: com.chromia.rell.doc.model.Doc_Def): String = when (def) {
        is com.chromia.rell.doc.model.Doc_Function -> def.kind.keyword.uppercase()
        is com.chromia.rell.doc.model.Doc_Property -> if (def.mutable) "MUTABLE PROPERTY" else "PROPERTY"
        is Doc_Class -> def.kind.keyword.uppercase()
        is com.chromia.rell.doc.model.Doc_TypeAlias -> "ALIAS"
    }

    private fun renderPage(
        site: Doc_Site,
        index: SiteIndex,
        markdown: Markdown,
        navigation: Navigation,
        relativePath: String,
        spec: PageSpec,
    ) {
        val typeRender = TypeRender(index, PageContext(relativePath, spec.ctx.currentPackage))
        val signature = SignatureRender(typeRender)
        val pages = Pages(site, markdown, navigation, signature)
        writePage(relativePath, pages.renderSitePage(spec))
    }

    private fun writePage(relativePath: String, html: String) {
        val file = outputDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(html)
    }

    /**
     * Minimal client-side redirect page. Uses both a `meta refresh` and a `location.replace` (the
     * latter avoids leaving the stub in browser history) plus a `<link rel="canonical">` so crawlers
     * follow it to the real anchor. [target] is relative to the stub's own directory.
     */
    private fun redirectHtml(target: String): String =
        """
        <!DOCTYPE html>
        <html lang="en"><head><meta charset="utf-8">
        <link rel="canonical" href="$target">
        <meta http-equiv="refresh" content="0; url=$target">
        <script>location.replace("$target" + location.search)</script>
        </head><body>Redirecting to <a href="$target">$target</a>…</body></html>
        """.trimIndent()

    private fun copyUserAssets(site: Doc_Site) {
        for (sheet in site.customStyleSheets) {
            if (!sheet.isRegularFile()) continue
            val target = outputDir / "styles" / sheet.fileName.toString()
            target.parent.createDirectories()
            sheet.copyTo(target, overwrite = true)
        }
        for (asset in site.customAssets) {
            if (!asset.isRegularFile()) continue
            val target = outputDir / "images" / asset.fileName.toString()
            target.parent.createDirectories()
            asset.copyTo(target, overwrite = true)
        }
    }

    private fun writeStaticAssets() {
        val css = outputDir / "styles" / "site.css"
        css.parent.createDirectories()
        css.writeText(SITE_CSS)
        (outputDir / "styles" / "fonts.css").writeText(FONTS_CSS)
        copyBundledFonts()
    }

    /**
     * Copy the chromia-docs web fonts (NBInternational family + Battlefin) out of the plugin's
     * jar into `<outputDir>/fonts/...` so the `@font-face` URLs in `FONTS_CSS` resolve at runtime.
     * Missing resources are silently skipped; the CSS already has a system-font fallback chain.
     */
    private fun copyBundledFonts() {
        for (resourcePath in BUNDLED_FONTS) {
            val stream = SiteRender::class.java.classLoader
                .getResourceAsStream("chromia/$resourcePath") ?: continue
            val target = outputDir.resolve(resourcePath)
            target.parent.createDirectories()
            stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    private companion object {
        val BUNDLED_FONTS: List<String> = listOf(
            "fonts/NBInternational/NBInternationalRegularWebfont.woff2",
            "fonts/NBInternational/NBInternationalRegularWebfont.ttf",
            "fonts/NBInternational/NBInternationalBoldWebfont.woff2",
            "fonts/NBInternational/NBInternationalBoldWebfont.ttf",
            "fonts/NBInternational/NBInternationalMonoWebfont.woff2",
            "fonts/NBInternational/NBInternationalMonoWebfont.ttf",
            "fonts/Battlefin-Black.woff2",
            "fonts/Battlefin-Black.otf",
        )
    }

    private fun writeSearchIndex(site: Doc_Site) {
        val json = Search.render(site)
        val dir = outputDir / "scripts"
        dir.createDirectories()
        // `pages.json` is kept for the legacy schema / external consumers. `pages.js` wraps the same
        // payload as a global assignment loaded via <script> — `fetch()` of a local file is blocked
        // under the file:// origin (CORS), so a script include is what makes search work when the
        // docs are opened straight off disk rather than served over HTTP.
        (dir / "pages.json").writeText(json)
        (dir / "pages.js").writeText("window.__rellDocsPages = $json;\n")
    }
}
