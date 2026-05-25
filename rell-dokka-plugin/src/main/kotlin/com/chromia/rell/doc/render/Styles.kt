/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import org.intellij.lang.annotations.Language

/**
 * Self-contained stylesheet for the generated doc site.
 *
 * Palette + typography pulled from the main Chromia Docs (Docusaurus theme in
 * `~/chromaway/chromia-docs/src/css/custom.css`): purple primary (`--ifm-color-primary`
 * = `#9e5ecf`), Chromia-grey neutrals, white background. We use system sans-serif as the body
 * font (the chromia-docs site ships the `NBInternationalRegular` woffs from `/fonts/...` but we
 * don't bundle those — closest free fallback is plain `system-ui`).
 *
 * The stylesheet is inlined into every page's `<style>` block; we also drop a copy at
 * `styles/site.css` for anyone who wants to override.
 */
@Suppress(
    "CssUnresolvedCustomProperty", "CssUnusedSymbol", "CssNoGenericFontName",
    // Font URLs resolve at run-time against the output directory the SiteRender extracts
    // bundled fonts into; the IDE sees the .kt file's directory and can't follow them.
    "CssUnknownTarget", "CssInvalidPropertyValue",
)
@Language("CSS")
internal val SITE_CSS: String = """
:root {
  /* Chromia palette (shared across themes) */
  --chromia-dark:        #1f1a23;
  --chromia-off-white:   #fff8f8;
  --chromia-grey:        #675e6e;
  --chromia-grey-lighter:#ede2e2;
  --chromia-grey-lightest:#f7eeee;
  --chromia-grey-darker: #403c44;
  --chromia-grey-darkest:#332d37;
  --chromia-purple:      #cc91f0;
  --chromia-purple-dark: #9e5ecf;

  /* Light-mode semantic tokens (the default). The `[data-theme="dark"]` rule below
     overrides these for the dark theme. */
  --primary:        #9e5ecf;
  --primary-dark:   #7130a2;
  --primary-light:  #c8a4e4;
  --primary-bg:     #f7f1fa;
  --ink:            #1f1a23;
  --ink-soft:       #332d37;
  --muted:          #675e6e;
  --faint:          #a3a0a8;
  --bg:             #ffffff;
  --bg-alt:         #fafafa;
  --surface:        #ffffff;
  --rule:           #e6e3e8;
  --rule-hair:      #f0eef1;
  --info-bg:        #e8f4fd;
  --info-border:    #4fa3df;
  --shadow-card:    0 6px 18px rgba(31,26,35,0.08);

  --sans: 'International-regular', system-ui, -apple-system, BlinkMacSystemFont,
          'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', Arial, sans-serif;
  --bold: 'International-bold', system-ui, -apple-system, BlinkMacSystemFont,
          'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', Arial, sans-serif;
  --mono: 'International-mono', 'JetBrains Mono', 'SF Mono', Menlo, Consolas, 'Liberation Mono', monospace;
}

[data-theme="dark"] {
  /* Mirrors chromia-docs' `[data-theme="dark"]` block — lighter primary purple,
     `--chromia-dark` background, light text, low-contrast neutrals. */
  --primary:        #cc91f0;
  --primary-dark:   #b45ee9;
  --primary-light:  #dcb3f5;
  --primary-bg:     rgba(204,145,240,0.10);
  --ink:            rgba(255,248,248,0.92);
  --ink-soft:       rgba(255,248,248,0.78);
  --muted:          rgba(255,248,248,0.55);
  --faint:          rgba(255,248,248,0.35);
  --bg:             #1f1a23;
  --bg-alt:         #261f2a;
  --surface:        #2a2330;
  --rule:           rgba(255,248,248,0.12);
  --rule-hair:      rgba(255,248,248,0.06);
  --info-bg:        rgba(79,163,223,0.12);
  --info-border:    #4fa3df;
  --shadow-card:    0 6px 18px rgba(0,0,0,0.35);
}

/* Web fonts bundled with the plugin — same NBInternational family the main Chromia Docs
   site uses (under `/static/fonts/` in chromia-docs). Battlefin is reserved for the brand-y
   `Battlefin` family but not currently wired into any selector. */
@font-face {
  font-family: 'International-regular';
  src: url("../fonts/NBInternational/NBInternationalRegularWebfont.ttf") format("truetype"),
       url("fonts/NBInternational/NBInternationalRegularWebfont.ttf") format("truetype");
  font-display: swap;
}
@font-face {
  font-family: 'International-bold';
  src: url("../fonts/NBInternational/NBInternationalBoldWebfont.ttf") format("truetype"),
       url("fonts/NBInternational/NBInternationalBoldWebfont.ttf") format("truetype");
  font-display: swap;
}
@font-face {
  font-family: 'International-mono';
  src: url("../fonts/NBInternational/NBInternationalMonoWebfont.ttf") format("truetype"),
       url("fonts/NBInternational/NBInternationalMonoWebfont.ttf") format("truetype");
  font-display: swap;
}
@font-face {
  font-family: 'Battlefin';
  src: url("../fonts/Battlefin-Black.otf") format("opentype"),
       url("fonts/Battlefin-Black.otf") format("opentype");
  font-display: swap;
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html { font-size: 16px; scroll-behavior: smooth; }
body {
  font-family: var(--sans);
  color: var(--ink); background: var(--bg);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale;
}
a {
  color: var(--primary-dark); text-decoration: none;
  transition: color .12s ease;
}
a:hover { color: var(--primary); text-decoration: underline; }
code { font-family: var(--mono); font-size: .9em; }

/* ─── Three-column layout ─────────────────────────────────────── */
.layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  min-height: 100vh;
  max-width: 1440px;
  margin: 0 auto;
}
.sidebar {
  background: var(--bg);
  border-right: 1px solid var(--rule);
  position: sticky; top: 0; height: 100vh; overflow-y: auto;
  padding: 0;
}
.sidebar-body {
  padding: 0 1.25rem 1.5rem;
}
main {
  padding: 2rem 3rem 4rem;
  max-width: 820px;
}

/* ─── Sidebar title (the site name link at the top) ──────────── */
.sidebar-head {
  display: flex; flex-direction: column; gap: .5rem;
  padding: 1.5rem 1.25rem 1rem;
  border-bottom: 1px solid var(--rule); background: var(--bg);
  position: sticky; top: 0; z-index: 20; margin-bottom: 1rem;
}
.sidebar-head-row {
  display: flex; align-items: center; gap: .6rem;
}
.version-pill {
  align-self: flex-start;
  display: inline-flex; align-items: center; gap: .4rem;
  font-family: var(--mono); font-weight: 600; font-size: .72rem;
  color: var(--primary-dark);
  background: var(--primary-bg);
  border: 1px solid var(--primary-light);
  border-radius: 999px;
  padding: .1rem .55rem;
  letter-spacing: .02em;
}
.version-pill::before {
  content: ''; width: 6px; height: 6px; border-radius: 50%;
  background: var(--primary); display: inline-block;
  /* Mono fonts have extra leading above the cap line, so flex-`center` puts the dot
     below the text's optical midline. Nudge up by 1px to align with the text x-height. */
  transform: translateY(-1px);
}
.sidebar .site-title {
  display: block; flex: 1; min-width: 0;
  font-family: var(--bold);
  font-size: 1.05rem; line-height: 1.25;
  color: var(--ink); font-weight: 700;
  letter-spacing: -0.01em;
  text-decoration: none;
  word-break: break-word;
}
.sidebar .site-title:hover { color: var(--primary-dark); text-decoration: none; }

.theme-toggle {
  flex: 0 0 32px; width: 32px; height: 32px;
  display: inline-flex; align-items: center; justify-content: center;
  background: transparent; border: 1px solid var(--rule);
  border-radius: 50%; cursor: pointer;
  color: var(--muted);
  transition: background .15s ease, color .15s ease, border-color .15s ease;
}
.theme-toggle:hover { background: var(--bg-alt); color: var(--primary-dark); border-color: var(--primary-light); }
.theme-toggle svg { width: 16px; height: 16px; display: block; }
.theme-toggle .icon-sun { display: none; }
.theme-toggle .icon-moon { display: block; }
[data-theme="dark"] .theme-toggle .icon-sun  { display: block; }
[data-theme="dark"] .theme-toggle .icon-moon { display: none; }

/* ─── Search ───────────────────────────────────────────────────── */
.search { position: relative; margin-bottom: 1.2rem; }
.search input {
  width: 100%; box-sizing: border-box; padding: .5rem .7rem .5rem 2rem;
  font-family: var(--sans); font-size: .85rem; color: var(--ink);
  background: var(--bg-alt); border: 1px solid var(--rule); border-radius: 999px;
  outline: none; line-height: 1.3;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' fill='%23675e6e'><path d='M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001c.03.04.062.078.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1.007 1.007 0 0 0-.115-.099zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0z'/></svg>");
  background-repeat: no-repeat; background-position: .6rem center; background-size: 14px 14px;
}
.search input:focus { border-color: var(--primary); background-color: var(--bg); }
.search-results {
  list-style: none; padding: .3rem 0;
  position: absolute; left: 0; right: 0; top: 100%; z-index: 30;
  max-height: 380px; overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--rule); border-radius: 8px;
  box-shadow: var(--shadow-card);
  margin: .3rem 0 0;
  font-family: var(--sans); line-height: 1.3;
}
.search-results:empty { display: none; }
.search-results li { margin: 0; padding: 0; }
.search-results a {
  display: block; padding: .35rem .75rem;
  color: var(--ink-soft); font-size: .82rem; text-decoration: none;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.search-results a:hover, .search-results a.focused {
  background: var(--primary-bg); color: var(--primary-dark); text-decoration: none;
}
.search-results .res-name { font-weight: 600; color: var(--ink); }
.search-results .res-pkg  { color: var(--muted); font-size: .72rem; margin-left: .4rem; font-weight: 400; }
.search-results a:hover .res-name, .search-results a.focused .res-name { color: var(--primary-dark); }
.search-empty { font-size: .76rem; color: var(--muted); font-style: italic; padding: .35rem .75rem; }

/* ─── Sidebar nav ──────────────────────────────────────────────── */
.sidebar nav { font-family: var(--sans); font-size: .88rem; }
.sidebar nav .nav-group { margin-bottom: 1rem; }
.sidebar nav .nav-module {
  font-size: .7rem; letter-spacing: .12em; text-transform: uppercase;
  color: var(--muted); font-weight: 700; margin-bottom: .55rem; line-height: 1.3;
}

.sidebar nav .nav-defs {
  list-style: none; padding-left: 0; margin: 0 0 .35rem;
}
.sidebar nav .nav-defs-root { margin-bottom: .8rem; }
.sidebar nav .nav-defs .nav-defs { padding-left: 1rem; }
.sidebar nav .nav-def-row {
  display: flex; align-items: center; gap: .45rem;
  padding: .15rem .55rem; border-radius: 4px;
  white-space: nowrap; overflow: hidden;
}
.sidebar nav .nav-def-row:hover { background: var(--bg-alt); }
.sidebar nav .nav-def-row a {
  color: var(--ink-soft); font-size: .82rem;
  text-overflow: ellipsis; overflow: hidden;
  text-decoration: none;
}
.sidebar nav .nav-def-row:hover a { color: var(--primary-dark); }
.sidebar nav .nav-def-row.deprecated a { text-decoration: line-through; text-decoration-color: var(--faint); color: var(--muted); }

.sidebar nav .nav-kind {
  display: inline-flex; align-items: center; justify-content: center;
  width: 16px; height: 16px; flex: 0 0 16px;
  font-size: .58rem; font-weight: 700; font-family: var(--mono);
  color: var(--surface); background: var(--muted);
  border-radius: 50%; line-height: 1;
}
.sidebar nav .nav-kind.kind-fn     { background: var(--primary-dark); }
.sidebar nav .nav-kind.kind-prop   { background: #4d617d; }
.sidebar nav .nav-kind.kind-entity { background: #1f6b4a; }
.sidebar nav .nav-kind.kind-object { background: #5e2e8a; }
.sidebar nav .nav-kind.kind-struct { background: #b07a1e; }
.sidebar nav .nav-kind.kind-enum   { background: #8a5e2e; text-transform: uppercase; }
.sidebar nav .nav-kind.kind-type   { background: var(--muted); }
.sidebar nav .nav-kind.kind-alias  { background: #ff405e; }

.sidebar nav .nav-pkg-block { margin: 0 0 .2rem; }
.sidebar nav .nav-pkg-summary {
  list-style: none; cursor: pointer;
  padding: .25rem .55rem .25rem 1.3rem;
  border-radius: 4px;
  position: relative;
}
.sidebar nav .nav-pkg-summary:hover { background: var(--bg-alt); }
.sidebar nav .nav-pkg-summary::-webkit-details-marker { display: none; }
.sidebar nav .nav-pkg-summary::before {
  content: '›'; color: var(--muted);
  position: absolute; left: .55rem; top: .25rem;
  font-size: 1rem; line-height: 1;
  transition: transform .15s ease;
}
.sidebar nav .nav-pkg-block[open] > .nav-pkg-summary::before { transform: rotate(90deg); }
.sidebar nav .nav-pkg {
  display: inline; color: var(--ink-soft); font-weight: 500;
  font-size: .85rem; text-decoration: none;
}
.sidebar nav .nav-pkg:hover { color: var(--primary-dark); }
.sidebar nav .nav-pkg-block.current > .nav-pkg-summary {
  background: var(--primary-bg);
}
.sidebar nav .nav-pkg-block.current > .nav-pkg-summary .nav-pkg {
  color: var(--primary-dark); font-weight: 700;
}

/* ─── Top-of-page header ───────────────────────────────────────── */
.doc-head {
  border-bottom: 1px solid var(--rule);
  margin-bottom: 2rem; padding-bottom: 1rem;
}
.doc-head-inner { padding: 0; }
.doc-title {
  font-family: var(--bold);
  font-size: clamp(1.8rem, 3vw, 2.4rem);
  letter-spacing: -0.015em; color: var(--ink);
  line-height: 1.2; margin-bottom: .5rem; font-weight: 700;
  word-break: break-word; overflow-wrap: anywhere;
}
.doc-kicker {
  font-family: var(--sans); font-size: .7rem; text-transform: uppercase;
  letter-spacing: .14em; color: var(--muted); font-weight: 700;
  margin-bottom: .6rem;
}
.doc-breadcrumbs {
  font-family: var(--sans); font-size: .82rem; color: var(--muted);
  margin-top: .8rem;
}
.doc-breadcrumbs a { color: var(--muted); text-decoration: none; }
.doc-breadcrumbs a:hover { color: var(--primary-dark); }
.doc-breadcrumbs .sep { color: var(--faint); margin: 0 .35rem; }

/* ─── Sections ─────────────────────────────────────────────────── */
.section { margin-bottom: 2.4rem; }
.section-head {
  padding: .8rem 0 .6rem; margin-bottom: 1rem;
}
.section-title {
  font-family: var(--bold); font-weight: 700;
  font-size: 1.35rem; letter-spacing: -0.01em; line-height: 1.3;
  color: var(--ink);
}

/* ─── Definition tables (package listings + class members) ───── */
.def-list { width: 100%; border-collapse: collapse; }
.def-list th, .def-list td {
  text-align: left; padding: .7rem .85rem; border-bottom: 1px solid var(--rule-hair);
  vertical-align: top; font-size: .9rem;
}
.def-list thead th {
  font-family: var(--sans); font-weight: 700;
  font-size: .72rem; text-transform: uppercase; letter-spacing: .1em;
  color: var(--muted); border-bottom: 1px solid var(--rule);
}
.def-list .def-name { font-family: var(--mono); font-weight: 500; min-width: 14em; }
.def-list .def-name a { color: var(--ink); text-decoration: none; }
.def-list .def-name a:hover { color: var(--primary-dark); }
.def-list tr.deprecated .def-name a { text-decoration: line-through; text-decoration-color: var(--faint); color: var(--muted); }
.def-list tr.deprecated .def-summary { color: var(--muted); }
.def-list .def-summary { color: var(--ink-soft); }
.def-list .def-summary code { font-size: .82em; background: var(--bg-alt); padding: 0 .3rem; border-radius: 3px; }

/* ─── Signature block ──────────────────────────────────────────── */
.signature {
  background: var(--bg-alt); border: 1px solid var(--rule);
  border-left: 3px solid var(--primary);
  padding: 1rem 1.2rem; margin-bottom: 1.2rem;
  border-radius: 4px;
}
.signature code {
  display: block; font-family: var(--mono); font-size: .9rem;
  color: var(--ink); white-space: pre-wrap; word-break: break-word;
  line-height: 1.55;
}
.signature .sig-kw    { color: var(--primary-dark); font-weight: 600; }
.signature .sig-name  { color: var(--ink); font-weight: 700; }
.signature .sig-param { color: #2f4968; }
.signature .sig-tp    { color: var(--primary-dark); }
.signature .sig-lit   { color: #1f6b4a; }
.signature .sig-str   { color: #1f6b4a; }
.signature .sig-ref   { color: var(--ink); }
.signature .sig-punct { color: var(--muted); }
.signature a.type-link { color: var(--primary-dark); text-decoration: underline; text-decoration-style: dotted; text-underline-offset: 3px; }
.signature a.type-link:hover { text-decoration-style: solid; }
.signature .type-name { color: var(--ink-soft); }

/* ─── Prose (markdown-rendered body) ───────────────────────────── */
.prose { font-size: .98rem; color: var(--ink); line-height: 1.7; }
.prose p { margin-bottom: 1rem; }
.prose code {
  font-family: var(--mono); font-size: .85rem;
  color: var(--primary-dark); background: var(--bg-alt);
  padding: .12rem .4rem; border-radius: 4px;
  border: 1px solid var(--rule-hair);
}
.prose ul, .prose ol { margin: 0 0 1rem 1.6rem; }
.prose li { margin-bottom: .35rem; }
.prose strong { color: var(--ink); font-weight: 700; }
.prose h2 { font-family: var(--bold); font-size: 1.5rem; margin: 1.6rem 0 .8rem; color: var(--ink); }
.prose h3 { font-family: var(--bold); font-size: 1.2rem; margin: 1.4rem 0 .6rem; color: var(--ink); }
.prose h4 { font-family: var(--bold); font-size: 1rem; margin: 1.2rem 0 .5rem; color: var(--ink); }
.prose pre {
  background: var(--bg-alt); border: 1px solid var(--rule);
  border-radius: 6px; padding: 1rem 1.2rem;
  overflow-x: auto; font-size: .85rem; line-height: 1.5;
  margin: 1rem 0;
}
.prose pre code { background: transparent; padding: 0; border: 0; color: var(--ink); }

/* Rell-fence syntax highlighting tokens — wired up by `RELL_HIGHLIGHT_JS`. The colour
   choices echo the signature-block palette so the eye reads them as the same syntax. */
.prose pre code .rhl-kw      { color: var(--primary-dark); font-weight: 600; }
.prose pre code .rhl-type    { color: #2f4968; }
.prose pre code .rhl-lit     { color: #1f6b4a; font-weight: 600; }
.prose pre code .rhl-num     { color: #1f6b4a; }
.prose pre code .rhl-str     { color: #1f6b4a; }
.prose pre code .rhl-bytes   { color: #b07a1e; }
.prose pre code .rhl-anno    { color: var(--primary); font-weight: 600; }
.prose pre code .rhl-comment { color: var(--muted); font-style: italic; }
[data-theme="dark"] .prose pre code .rhl-type    { color: #9ab4d0; }
[data-theme="dark"] .prose pre code .rhl-lit     { color: #7fb89a; }
[data-theme="dark"] .prose pre code .rhl-num     { color: #7fb89a; }
[data-theme="dark"] .prose pre code .rhl-str     { color: #b5dab9; }
[data-theme="dark"] .prose pre code .rhl-bytes   { color: #d9a86c; }
.prose table { border-collapse: collapse; margin: 1rem 0; width: 100%; }
.prose th, .prose td { border-bottom: 1px solid var(--rule-hair); padding: .45rem .7rem; text-align: left; }
.prose th { background: var(--bg-alt); font-weight: 700; }
.prose blockquote {
  margin: 1rem 0; padding: .6rem 1rem;
  background: var(--info-bg); border-left: 3px solid var(--info-border);
  color: var(--ink-soft); font-style: normal;
  border-radius: 0 4px 4px 0;
}
.prose a { color: var(--primary-dark); text-decoration: underline; text-decoration-color: var(--primary-light); text-underline-offset: 2px; }
.prose a:hover { text-decoration-color: var(--primary-dark); }

.source-link {
  font-family: var(--sans); font-size: .8rem;
  color: var(--muted); margin-bottom: 1rem;
}
.source-link a { color: var(--muted); }
.source-link a:hover { color: var(--primary-dark); }

/* Deprecated decls: just a strikethrough; no orange callout. */
.deprecated-note {
  font-size: .9rem; color: var(--muted); margin: .5rem 0 1rem;
}
.deprecated-note s { color: var(--ink-soft); font-weight: 600; text-decoration-color: var(--muted); }

.empty {
  font-family: var(--sans); color: var(--muted); font-style: italic;
  padding: 1rem 0;
}

.colophon {
  margin-top: 3rem;
  border-top: 1px solid var(--rule);
  padding: 1.2rem 0 .8rem;
  font-family: var(--sans); font-size: .92rem;
  color: var(--ink-soft);
  display: flex; flex-wrap: wrap; align-items: center; gap: .35rem;
}
.colophon-sep { color: var(--faint); }
.colophon-version {
  display: inline-flex; align-items: center; gap: .4rem;
  font-family: var(--mono); font-weight: 600;
  color: var(--primary-dark);
  background: var(--primary-bg);
  border: 1px solid var(--primary-light);
  border-radius: 999px;
  padding: .15rem .6rem;
  font-size: .8rem;
}
.colophon-version::before {
  content: ''; width: 6px; height: 6px; border-radius: 50%;
  background: var(--primary); display: inline-block;
}

/* Strikethrough the page title when the def itself is deprecated. The
   detail-page renderer adds .deprecated to .doc-title when it sees `def.deprecated != null`. */
.doc-title.deprecated { text-decoration: line-through; text-decoration-color: var(--faint); color: var(--muted); }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .sidebar { position: static; height: auto; border-right: 0; border-bottom: 1px solid var(--rule); }
  main { padding: 1.4rem 1.4rem 2.5rem; }
}
""".trimIndent()
