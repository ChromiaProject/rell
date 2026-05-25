/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML

/**
 * Builds the side-bar `<nav>` markup that ships at the top of every page. Each module gets a
 * heading; each non-hidden package is a clickable link with its declarations nested underneath.
 *
 * The standalone `navigation.html` file is the same payload rendered without a wrapping
 * `<main>` — kept so callers can `<iframe>` it the way the old Dokka plugin's nav worked. It
 * also re-uses the chrome of the index page (header, sidebar layout) so it stays usable when
 * loaded directly.
 */
internal class Navigation(private val site: Doc_Site) {

    fun renderInto(out: FlowContent, ctx: PageContext) {
        // Relative paths to two assets the client-side search needs: the index JSON and the
        // site-root directory it resolves search-hit `location`s against. Computed per-page so
        // the search keeps working regardless of how deep the current page sits in the tree.
        val pagesJsonHref = Hrefs.relativeFrom(ctx.relativePath, "scripts/pages.json")
        val siteRootHref = Hrefs.relativeFrom(ctx.relativePath, "index.html").removeSuffix("index.html")
        out.div(classes = "search") {
            input(type = InputType.search) {
                id = "doc-search"
                attributes["placeholder"] = "Search…"
                attributes["autocomplete"] = "off"
                attributes["data-pages-json"] = pagesJsonHref
                attributes["data-site-root"] = siteRootHref
            }
            ul(classes = "search-results") { id = "doc-search-results" }
            script {
                unsafe { +SEARCH_JS }
            }
        }
        // The sidebar already renders the site title at the top; for the typical single-module
        // case (system lib, or one dapp) repeating the module name as a section heading inside
        // the nav is just visual noise. Skip the heading and inline the packages directly.
        val showModuleHeadings = site.modules.size > 1
        out.nav(classes = "doc-nav") {
            for (module in site.modules) {
                div(classes = "nav-group") {
                    if (showModuleHeadings) div(classes = "nav-module") { +module.name }

                    val visible = module.packages.filter { it.qname !in site.hiddenPackages }
                    // Root package goes first and is rendered inline (no collapsible wrapper) so
                    // its top-level defs read as the module's primary entry points.
                    val rootPkg = visible.firstOrNull { it.qname.isEmpty() }
                    val otherPkgs = visible.filter { it.qname.isNotEmpty() }
                        .sortedBy { it.qname }

                    rootPkg?.let { renderRootPkg(this, ctx, module, it) }
                    for (pkg in otherPkgs) renderCollapsiblePkg(this, ctx, module, pkg)
                }
            }
        }
    }

    private fun renderRootPkg(
        out: FlowContent,
        ctx: PageContext,
        module: Doc_Module,
        pkg: Doc_Package,
    ) {
        out.ul(classes = "nav-defs nav-defs-root") {
            for (def in pkg.defs.sortedBy { it.name }) renderDefLi(this, ctx, module, pkg, def)
        }
    }

    private fun renderCollapsiblePkg(
        out: FlowContent,
        ctx: PageContext,
        module: Doc_Module,
        pkg: Doc_Package,
    ) {
        val pkgPath = Paths.packageIndexPath(module, pkg)
        val pkgHref = Hrefs.relativeFrom(ctx.relativePath, pkgPath)
        val current = ctx.currentPackage?.qname == pkg.qname
        // `<details>` does the toggle natively — no JS needed. Pre-open the collapsible the
        // user is currently viewing so the active def is visible.
        out.details(classes = if (current) "nav-pkg-block current" else "nav-pkg-block") {
            if (current) attributes["open"] = "open"
            summary(classes = "nav-pkg-summary") {
                a(href = pkgHref, classes = "nav-pkg toc--link") {
                    attributes["anchor-label"] = pkg.qname
                    +pkg.displayName
                }
            }
            if (pkg.defs.isNotEmpty()) {
                ul(classes = "nav-defs") {
                    for (def in pkg.defs.sortedBy { it.name }) renderDefLi(this, ctx, module, pkg, def)
                }
            }
        }
    }

    private fun renderDefLi(
        out: UL,
        ctx: PageContext,
        module: Doc_Module,
        pkg: Doc_Package,
        def: Doc_Def,
    ) {
        val href = Hrefs.relativeFrom(ctx.relativePath, Paths.pageRelativePath(module, pkg, def))
        val rowClasses = if (def.deprecated != null) "nav-def-row deprecated" else "nav-def-row"
        out.li(classes = rowClasses) {
            span(classes = "nav-kind ${kindClass(def)}") { +kindBadge(def) }
            a(href = href, classes = "toc--link nav-def-link") {
                attributes["anchor-label"] = def.qname
                +renderDefNavLabel(def)
            }
        }
    }

    private fun kindClass(def: Doc_Def): String = when (def) {
        is Doc_Function -> "kind-fn"
        is Doc_Property -> "kind-prop"
        is Doc_TypeAlias -> "kind-alias"
        is Doc_Class -> when (def.kind) {
            Doc_ClassKind.ENTITY -> "kind-entity"
            Doc_ClassKind.OBJECT -> "kind-object"
            Doc_ClassKind.STRUCT -> "kind-struct"
            Doc_ClassKind.ENUM -> "kind-enum"
            Doc_ClassKind.TYPE -> "kind-type"
        }
    }

    private fun kindBadge(def: Doc_Def): String = when (def) {
        is Doc_Function -> "f"
        is Doc_Property -> "p"
        is Doc_TypeAlias -> "a"
        is Doc_Class -> when (def.kind) {
            Doc_ClassKind.ENTITY -> "e"
            Doc_ClassKind.OBJECT -> "o"
            Doc_ClassKind.STRUCT -> "s"
            Doc_ClassKind.ENUM -> "E"
            Doc_ClassKind.TYPE -> "t"
        }
    }

    fun renderStandalone(): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendHTML().html {
            attributes["lang"] = "en"
            head {
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                title("${escapeHtml(site.title)} — Navigation")
                style { unsafe { +SITE_CSS } }
            }
            body {
                div(classes = "layout nav-standalone") {
                    div(classes = "sidebar") {
                        span(classes = "site-title") { +site.title }
                        renderInto(this, PageContext(relativePath = "navigation.html", currentPackage = null))
                    }
                    div { h1 { +"Navigation" } }
                }
            }
        }
    }
}

private fun renderDefNavLabel(def: Doc_Def): String =
    if (def is Doc_Function) "${def.name}()" else def.name

/**
 * Tiny client-side search: fetches `scripts/pages.json` on first focus of the input, keeps the
 * decoded records in memory, and re-filters on every keystroke. Matches are token-based
 * (case-insensitive substring against `name`, `description`, and `searchKeys`); results are
 * capped at 20 to keep render cost trivial.
 *
 * Inlined as a `<script>` (no external file) so the search works whether the site is served
 * over HTTP or opened from disk; the only network round-trip is the one-time JSON fetch.
 */
@org.intellij.lang.annotations.Language("JavaScript")
internal const val SEARCH_JS = """
(function() {
  if (window.__rellDocsSearchInit) return; window.__rellDocsSearchInit = true;
  document.addEventListener('DOMContentLoaded', function() {
    const input = document.getElementById('doc-search');
    const resultsEl = document.getElementById('doc-search-results');
    if (!input || !resultsEl) return;
    const pagesUrl = input.getAttribute('data-pages-json');
    const rootUrl  = input.getAttribute('data-site-root') || '';
    let records = null;
    let focused = -1;

    function loadOnce() {
      if (records !== null) return Promise.resolve(records);
      return fetch(pagesUrl).then(function(r) { return r.json(); })
        .then(function(json) { records = json; return records; })
        .catch(function(err) { records = []; console.error('rell-docs search:', err); return records; });
    }

    function clearResults() { resultsEl.innerHTML = ''; focused = -1; }

    function render(matches, query) {
      clearResults();
      if (!query) return;
      if (matches.length === 0) {
        const li = document.createElement('li');
        li.className = 'search-empty';
        li.textContent = 'No matches';
        resultsEl.appendChild(li);
        return;
      }
      matches.forEach(function(rec, idx) {
        const li = document.createElement('li');
        const a = document.createElement('a');
        a.href = rootUrl + rec.location;
        const name = document.createElement('span');
        name.className = 'res-name';
        name.textContent = rec.name;
        a.appendChild(name);
        if (rec.description) {
          const pkg = document.createElement('span');
          pkg.className = 'res-pkg';
          pkg.textContent = rec.description;
          a.appendChild(pkg);
        }
        a.setAttribute('data-idx', idx);
        li.appendChild(a);
        resultsEl.appendChild(li);
      });
    }

    function filter(query) {
      query = query.trim().toLowerCase();
      if (!query) return [];
      const out = [];
      const terms = query.split(/\s+/);
      for (let i = 0; i < records.length && out.length < 20; i++) {
        const r = records[i];
        const hay = (r.name + ' ' + (r.description || '') + ' ' + (r.searchKeys || []).join(' ')).toLowerCase();
        let ok = true;
        for (let t = 0; t < terms.length; t++) {
          if (hay.indexOf(terms[t]) < 0) { ok = false; break; }
        }
        if (ok) out.push(r);
      }
      // Prefer exact prefix on `name` to substring elsewhere.
      out.sort(function(a, b) {
        const an = a.name.toLowerCase(), bn = b.name.toLowerCase();
        const ai = an.indexOf(query), bi = bn.indexOf(query);
        if (ai === bi) return an.localeCompare(bn);
        if (ai === -1) return 1;
        if (bi === -1) return -1;
        return ai - bi;
      });
      return out;
    }

    input.addEventListener('input', function() {
      loadOnce().then(function() { render(filter(input.value), input.value.trim()); });
    });

    input.addEventListener('focus', function() { loadOnce(); });

    input.addEventListener('keydown', function(e) {
      const links = resultsEl.querySelectorAll('a');
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        if (links.length === 0) return;
        focused = Math.min(focused + 1, links.length - 1);
        updateFocus(links);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        if (links.length === 0) return;
        focused = Math.max(focused - 1, 0);
        updateFocus(links);
      } else if (e.key === 'Enter') {
        if (focused >= 0 && links[focused]) {
          e.preventDefault();
          window.location.href = links[focused].href;
        } else if (links.length > 0) {
          e.preventDefault();
          window.location.href = links[0].href;
        }
      } else if (e.key === 'Escape') {
        input.value = '';
        clearResults();
        input.blur();
      }
    });

    function updateFocus(links) {
      for (let i = 0; i < links.length; i++) {
        links[i].classList.toggle('focused', i === focused);
      }
      if (focused >= 0) links[focused].scrollIntoView({ block: 'nearest' });
    }
  });
})();
"""
