/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.report

import kotlinx.html.*
import org.intellij.lang.annotations.Language
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs

internal const val ACCENT_HEX = "#C1440E"
internal const val INK_HEX = "#0F0F10"

internal const val RELL_BLOB = "https://gitlab.com/chromaway/rell/-/blob/dev"

internal fun HEAD.linkWebFonts() {
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }

    link(
        rel = "stylesheet",
        href = "https://fonts.googleapis.com/css2?family=Geist:wght@300;400;500;600;700" +
                "&family=JetBrains+Mono:wght@400;500;600;700&display=swap",
    )
}

internal fun HEAD.titleWithTimestamp(prefix: String, generatedAt: Instant) {
    title("$prefix — ${generatedAt.toHuman()}")
}

internal fun FlowContent.renderDocHead(title: String, metaPairs: List<Pair<String, String>> = emptyList()) {
    header(classes = "doc-head") {
        div(classes = "doc-head-inner") {
            h1(classes = "doc-title") { +title }
            if (metaPairs.isNotEmpty()) {
                div(classes = "doc-meta") {
                    metaPairs.forEach { (k, v) ->
                        span(classes = "doc-meta-pair") {
                            span(classes = "k") { +k }
                            span(classes = "v") { +v }
                        }
                    }
                }
            }
        }
    }
}

internal fun FlowContent.renderColophon(meta: String, generatedAt: Instant) = footer(classes = "colophon") {
    div(classes = "colophon-inner") {
        span { a(href = "https://gitlab.com/chromaway/rell") { +"chromaway/rell" } }
        span(classes = "colophon-spacer") {}
        span(classes = "colophon-meta") { +meta }
        span(classes = "sep") { +"·" }
        span(classes = "colophon-meta") { +generatedAt.toHuman() }
    }
}

internal inline fun FlowContent.renderSection(
    title: String,
    strap: String = "",
    crossinline body: FlowContent.() -> Unit,
) = section(classes = "section") {
    div(classes = "section-head") {
        div(classes = "section-title") { +title }
        if (strap.isNotEmpty()) div(classes = "section-strap") { +strap }
    }
    div(classes = "section-body") { body() }
}

internal fun FlowContent.metric(label: String, value: String, unit: String, sub: String) = div(classes = "metric") {
    div(classes = "metric-label") { +label }
    div(classes = "metric-value") {
        +value
        if (unit.isNotEmpty()) span(classes = "metric-unit") { +" $unit" }
    }
    div(classes = "metric-sub") { +sub }
}

internal inline fun FlowContent.sysinfoBlock(title: String, crossinline body: DL.() -> Unit) =
    div(classes = "sysinfo-block") {
        h3 { +title }
        dl { body() }
    }

internal fun DL.dlRow(key: String, value: String, mono: Boolean = false) {
    dt { +key }
    dd {
        if (mono) attributes["class"] = "mono"
        +value
    }
}

internal fun Instant.toHuman(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd · HH:mm z"))

internal fun String.formatRoot(vararg args: Any?): String = format(Locale.ROOT, *args)

internal fun Long.humanBytes(): String {
    if (this == 0L) return "0 B"

    var v = this.toDouble()

    for (unit in arrayOf("B", "KB", "MB", "GB")) {
        if (abs(v) < 1024)
            return "%.1f %s".formatRoot(v, unit)

        v /= 1024.0
    }

    return "%.1f TB".formatRoot(v)
}

@Suppress("CssUnresolvedCustomProperty", "CssUnusedSymbol", "CssNoGenericFontName")
@Language("CSS")
internal val BASE_CSS: String = """
:root {
  --ink:        #0F0F10;
  --ink-soft:   #2B2B2F;
  --muted:      #6B6D73;
  --faint:      #A8A9AE;
  --bg:         #FAFAF7;
  --bg-alt:     #F2F2EE;
  --surface:    #FFFFFF;
  --rule:       rgba(15,15,16,0.12);
  --rule-hair:  rgba(15,15,16,0.06);
  --accent:     #C1440E;
  --accent-bg:  rgba(193,68,14,0.06);
  --sans: 'Geist', -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Arial, sans-serif;
  --mono: 'JetBrains Mono', 'SF Mono', 'Cascadia Code', Consolas, ui-monospace, monospace;
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html { font-size: 15px; scroll-behavior: smooth; }
body {
  font-family: var(--sans);
  color: var(--ink);
  background: var(--bg);
  line-height: 1.55;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  font-feature-settings: "ss01", "cv01";
}
a { color: var(--accent); text-decoration: none; border-bottom: 1px solid transparent; transition: border-color .15s ease; }
a:hover { border-bottom-color: var(--accent); }

.doc-head { background: var(--surface); border-bottom: 2px solid var(--ink); }
.doc-head-inner { max-width: 1180px; margin: 0 auto; padding: 1.5rem 2rem 1.2rem; }
.doc-title {
  font-family: var(--mono); font-weight: 600;
  font-size: clamp(1.7rem, 3.4vw, 2.4rem);
  letter-spacing: -0.03em; color: var(--ink); line-height: 1.05; margin-bottom: .7rem;
}
.doc-meta { display: flex; flex-wrap: wrap; gap: .35rem 1.1rem; font-family: var(--mono); font-size: .74rem; color: var(--ink-soft); }
.doc-meta-pair { display: inline-flex; gap: .4rem; }
.doc-meta-pair .k {
  color: var(--muted); text-transform: uppercase; letter-spacing: .12em;
  font-size: .68rem; font-weight: 600; line-height: 1.6;
}
.doc-meta-pair .v { color: var(--ink); font-weight: 500; font-variant-numeric: tabular-nums; }

main { max-width: 1180px; margin: 0 auto; padding: 2rem 2rem 2.5rem; }

.section { margin-bottom: 2.4rem; position: relative; }
.section-head {
  padding: 1rem 0 .8rem; margin-bottom: 1.1rem;
  border-top: 1px solid var(--ink);
}
.section-title {
  font-family: var(--mono); font-weight: 600;
  font-size: 1.05rem; letter-spacing: -0.01em; line-height: 1.25;
  color: var(--ink); text-transform: lowercase;
  white-space: pre-wrap;
}
.section-title::before { content: '# '; color: var(--faint); font-weight: 400; }
.section-strap { margin-top: .25rem; font-family: var(--sans); font-size: .82rem; color: var(--muted); max-width: 64ch; line-height: 1.5; }
.section-body h3 {
  font-family: var(--mono); font-size: .68rem; font-weight: 700;
  text-transform: uppercase; letter-spacing: .18em; color: var(--muted);
  margin-bottom: .7rem; padding-bottom: .35rem;
  border-bottom: 1px solid var(--rule-hair);
}
.section-body h3.sub { margin-top: 1.4rem; }
.section-body > h3.sub:first-child { margin-top: 0; }
.prose { font-size: .92rem; color: var(--ink-soft); margin-bottom: .8rem; }
.prose code {
  font-family: var(--mono); font-size: .82rem;
  color: var(--ink); background: var(--bg-alt);
  padding: 1px 5px; border-radius: 2px;
}

.metrics {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  background: var(--surface); border: 1px solid var(--rule);
}
.metric { padding: 1rem 1.2rem; border-left: 1px solid var(--rule-hair); display: flex; flex-direction: column; justify-content: center; min-height: 92px; }
.metric:first-child { border-left: 0; }
.metric-label {
  font-family: var(--mono); font-size: .66rem;
  text-transform: uppercase; letter-spacing: .18em;
  color: var(--muted); font-weight: 600; margin-bottom: .35rem;
}
.metric-value {
  font-family: var(--mono); font-size: 1.8rem; font-weight: 600;
  line-height: 1; letter-spacing: -0.02em;
  color: var(--ink); font-variant-numeric: tabular-nums;
}
.metric-unit { font-family: var(--mono); font-size: .8rem; font-weight: 500; color: var(--muted); margin-left: .1rem; letter-spacing: 0; }
.metric-sub { margin-top: .3rem; font-size: .72rem; color: var(--muted); font-family: var(--mono); }

.sysinfo-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  background: var(--surface); border: 1px solid var(--rule);
}
.sysinfo-block { padding: 1rem 1.2rem; border-left: 1px solid var(--rule-hair); }
.sysinfo-block:first-child { border-left: 0; }
.sysinfo-block dl { display: grid; grid-template-columns: max-content 1fr; gap: .3rem .8rem; font-size: .8rem; }
.sysinfo-block dt { color: var(--muted); font-size: .72rem; font-family: var(--mono); font-weight: 500; letter-spacing: .02em; }
.sysinfo-block dd { color: var(--ink); font-family: var(--sans); font-size: .82rem; word-break: break-all; }
.mono { font-family: var(--mono); font-size: .76rem; }

table { border-collapse: collapse; width: 100%; }
.table-scroll { overflow-x: auto; margin: 0 -.75rem; padding: 0 .75rem; }
thead th {
  text-align: left; font-family: var(--mono); font-weight: 600;
  font-size: .68rem; text-transform: uppercase; letter-spacing: .18em;
  color: var(--muted); padding: .5rem .75rem;
  border-bottom: 1px solid var(--ink); background: transparent;
}
thead th.num { text-align: right; }
td { padding: .55rem .75rem; border-bottom: 1px solid var(--rule-hair); vertical-align: middle; font-size: .85rem; }
tbody tr:hover { background: rgba(193,68,14,0.04); }
td.name { font-family: var(--sans); font-weight: 500; }
.num {
  text-align: right; font-variant-numeric: tabular-nums;
  font-family: var(--mono); font-size: .82rem; color: var(--ink);
}
.num.strong { font-weight: 700; color: var(--ink); }
.rank { color: var(--muted); font-weight: 600; width: 40px; }

.colophon { margin-top: 3rem; border-top: 1px solid var(--rule); background: var(--surface); }
.colophon-inner {
  max-width: 1180px; margin: 0 auto; padding: 1.1rem 2rem 1.3rem;
  font-family: var(--mono); font-size: .7rem; letter-spacing: .06em;
  color: var(--muted); display: flex; gap: .5rem; flex-wrap: wrap; align-items: center;
}
.colophon-inner .sep { color: var(--faint); }
.colophon-spacer { flex: 1; }
.colophon-meta { color: var(--faint); font-size: .66rem; letter-spacing: .02em; }

.empty {
  font-family: var(--sans); color: var(--muted); font-style: italic;
  padding: 1rem 0;
}

@media (max-width: 720px) {
  html { font-size: 14px; }
  .doc-head-inner, main, .colophon-inner { padding-left: 1.1rem; padding-right: 1.1rem; }
  .sysinfo-grid, .metrics { grid-template-columns: 1fr; }
  .sysinfo-block, .metric { border-left: 0; border-top: 1px solid var(--rule-hair); }
  .sysinfo-block:first-child, .metric:first-child { border-top: 0; }
}

@media print {
  body { background: #fff; }
  .section { break-inside: avoid; }
  a { color: var(--ink); border-bottom: 0; }
}
""".trimIndent()
