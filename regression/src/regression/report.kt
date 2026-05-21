/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import net.postchain.rell.performance.report.*
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

class ReportCommand: CliktCommand(name = "report") {
    override fun help(context: Context) =
        "Merge reports/parts/*.json into reports/results.json and render reports/report.html."

    private val reportsDir: Path by option(
        "--reports-dir",
        help = "Directory holding parts/, logs/, results.json, and the rendered report.html.",
    ).path().required()

    override fun run() {
        val results = mergeFragments(reportsDir)
        reportsDir.createDirectories()
        val resultsPath = reportsDir / "results.json"
        regressionWriter.writeValue(resultsPath.toFile(), results)

        val summary = results.results
            .groupBy { it.executionBackend }
            .mapValues { (_, rs) -> rs.groupingBy { it.status }.eachCount() }
        log("report", "Merged ${results.results.size} fragment(s) into $resultsPath — $summary")

        renderHtml(results, reportsDir)
    }
}

fun renderHtml(results: ResultsFile, reportsDir: Path) {
    reportsDir.createDirectories()
    val outFile = reportsDir / "report.html"
    val generatedAt = Instant.ofEpochMilli(results.generatedAtEpochMs)
    val html = buildString {
        appendLine("<!DOCTYPE html>")
        appendHTML().html {
            attributes["lang"] = "en"
            head {
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                titleWithTimestamp("Rell Regression", generatedAt)
                linkWebFonts()
                style { unsafe { +(BASE_CSS + REGRESSION_CSS) } }
            }
            body {
                val totalSeconds = results.results.sumOf { it.durationMs ?: 0L } / 1000.0
                renderDocHead(
                    "Rell Regression",
                    listOf("Rell" to results.rellVersion),
                )
                main {
                    renderEnvironment(results)
                    renderFlavorsOrFlat(results.results)
                    renderMethodology()
                }
                // Total compile time goes in the colophon, not the key metrics — reviewers
                // act on per-project status in the cohort tables below, not on the running total.
                renderColophon(
                    "results.json · ${"%.1f".formatRoot(totalSeconds)}s total chr time",
                    generatedAt,
                )
            }
        }
    }
    outFile.writeText(html)
    log("report", "Wrote $outFile (${results.results.size} project(s))")
}

private fun FlowContent.renderEnvironment(results: ResultsFile) = renderSection("environment") {
    val host = HostInfo.fromSystem()
    val jvm = JvmInfo.fromSystem()
    div(classes = "sysinfo-grid") {
        hostBlock(host)
        jvmBlock(jvm, pathLabel = "Path")
        sysinfoBlock("Rell") {
            dlRow("Version", results.rellVersion, mono = true)
            dlRow("Run at", Instant.ofEpochMilli(results.generatedAtEpochMs).toString(), mono = true)
        }
    }
}

private fun FlowContent.renderSummary(results: List<CompileResult>) = renderSection("summary") {
    val passed = results.count { it.status == Status.PASSED || it.status == Status.EXPECTED_FAIL }
    val total = results.size
    val rate = if (total > 0) 100.0 * passed / total else 0.0

    div(classes = "metrics") {
        metric("Projects", total.toString(), "")
        metric("Pass rate", "%.0f".formatRoot(rate), "%", "including expected fails")
    }
}

/**
 * Branch on whether the results carry per-backend tagging. Legacy results.json (everything is
 * [executionBackend][CompileResult.executionBackend] = null) and partial runs that only cover
 * one backend render the same flat summary + project tables as the single-flavor era. When
 * two or more distinct backends are present, we wrap each backend's panel in a CSS-only
 * `:checked ~` switcher so reviewers can flip between flavors without leaving the page.
 */
private fun FlowContent.renderFlavorsOrFlat(results: List<CompileResult>) {
    val backends = results.mapNotNull { it.executionBackend }.distinct()
    if (backends.size < 2) {
        renderSummary(results)
        renderProjectSections(results)
    } else {
        renderFlavorSwitcher(results, backends)
    }
}

/**
 * Renders the radio-input/label tabbar plus one panel per backend. The radios and panels are
 * direct children of `<main>` so the `.flavor-radio:checked ~ .flavor-panel-<x>` sibling
 * combinator in REGRESSION_CSS can drive the switch with no JavaScript.
 */
private fun FlowContent.renderFlavorSwitcher(results: List<CompileResult>, backends: List<ExecutionBackend>) {
    // Stable, deterministic order — INTERPRETER first so it's the default-checked tab.
    val ordered = backends.sortedBy { it.ordinal }
    for ((idx, backend) in ordered.withIndex()) {
        radioInput(name = "flavor-switch", classes = "flavor-radio") {
            id = backend.radioId()
            if (idx == 0) checked = true
        }
    }
    div(classes = "flavor-switch") {
        for (backend in ordered) {
            label(classes = "flavor-tab") {
                attributes["for"] = backend.radioId()
                +backend.label()
            }
        }
    }
    for (backend in ordered) {
        div(classes = "flavor-panel flavor-panel-${backend.name.lowercase()}") {
            val perBackend = results.filter { it.executionBackend == backend }
            renderSummary(perBackend)
            renderProjectSections(perBackend)
        }
    }
}

private fun ExecutionBackend.radioId(): String = "flavor-${name.lowercase()}"

/**
 * Renders one `renderSection` per cohort (public, private, …) so the public results stay
 * visually distinct from any private overlay. If a project came from a config file without a
 * stamped cohort (older results.json) it lands in a `projects` section so the report still
 * renders cleanly.
 */
private fun FlowContent.renderProjectSections(results: List<CompileResult>) {
    // Sort: failed first (what reviewers actually care about), then everything else in original order.
    val priority = mapOf(
        Status.FAILED to 0,
        Status.CLONE_FAILED to 1,
        Status.EXPECTED_FAIL to 2,
        Status.PASSED to 3,
    )
    val byCohort = results
        .withIndex()
        .groupBy { it.value.cohort ?: "projects" }
        .toSortedMap(compareBy { if (it == "public") 0 else if (it == "private") 1 else 2 })

    byCohort.forEach { (cohort, rowsWithIdx) ->
        val rows = rowsWithIdx
            .sortedWith(compareBy({ priority[it.value.status] ?: 99 }, { it.index }))
            .map { it.value }
        renderCohortTable(cohort, rows)
    }
}

private fun FlowContent.renderCohortTable(cohort: String, rows: List<CompileResult>) = renderSection(
    title = "$cohort projects",
    strap = "${rows.size} project${if (rows.size == 1) "" else "s"} · click a failed " +
            "pipeline stage to read the last few lines of chr stderr.",
) {
    div(classes = "table-scroll") {
        table(classes = "regression") {
            thead {
                tr {
                    th { +"Project" }
                    th { +"Status" }
                    th { +"Source" }
                    th { +"Pipeline" }
                    th(classes = "num") { +"Duration" }
                }
            }
            tbody {
                for (row in rows) renderRow(row)
            }
        }
    }
}

private fun TBODY.renderRow(r: CompileResult) {
    tr(classes = "row-${r.status.cssClass()}") {
        td(classes = "name") {
            div { +r.name }
            if (r.notes.isNotBlank()) div(classes = "row-notes") { +r.notes }
        }
        td { renderStatusBadge(r) }
        td(classes = "source") {
            a(href = r.url) {
                attributes["target"] = "_blank"
                attributes["rel"] = "noopener"
                +shortenUrl(r.url)
            }
            r.ref?.let {
                +" "
                span(classes = "mono ref") { +"@$it" }
            }
            r.sha?.let {
                +" "
                code { +it.take(10) }
            }
        }
        td(classes = "pipeline-cell") { renderPipeline(r) }
        td(classes = "num") {
            r.durationMs?.let { +"%.1f s".formatRoot(it / 1000.0) } ?: run { +"—" }
        }
    }
    // Failure log lives on a second row that spans the table — gives the chr stderr excerpt
    // room to wrap without being squeezed into the pipeline cell. Collapsed by default so the
    // table stays scannable; user expands on demand.
    if (r.errorSummary != null) {
        tr(classes = "log-row row-${r.status.cssClass()}") {
            td(classes = "log-cell") {
                attributes["colspan"] = "5"
                details {
                    summary { +"chr log excerpt (last lines)" }
                    pre(classes = "log-excerpt") { +r.errorSummary }
                    r.logRelPath?.let {
                        div(classes = "log-link") {
                            a(href = it) { +"full log" }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.renderStatusBadge(r: CompileResult) {
    span(classes = "badge badge-${r.status.cssClass()}") {
        +r.status.label()
    }
}

// GitLab-style pipeline strip: clone → install → build → test, one stage per chr command.
// Each stage carries an icon (✓ / ✗ / ⊘ / ·) that mirrors whether it ran, succeeded, was the
// failure point, or got skipped because an earlier step short-circuited the pipeline.
private enum class StageState { PASS, FAIL, XFAIL, SKIPPED }

private fun FlowContent.renderPipeline(r: CompileResult) {
    span(classes = "pipeline") {
        val stages = mutableListOf<Pair<String, StageState>>()

        val cloneState = if (r.status == Status.CLONE_FAILED) StageState.FAIL else StageState.PASS
        stages += "clone" to cloneState

        if (r.commands.isEmpty()) {
            // results.json predates the commands field; fall back to a single placeholder so
            // the cell isn't blank, but don't pretend we know which steps ran.
            stages += "chr" to (when (r.status) {
                Status.PASSED -> StageState.PASS
                Status.EXPECTED_FAIL -> StageState.XFAIL
                Status.CLONE_FAILED -> StageState.SKIPPED
                Status.FAILED -> StageState.FAIL
            })
        } else if (r.status == Status.CLONE_FAILED) {
            // Never got to chr — render every command as skipped to make the gap explicit.
            for (it in r.commands) {
                stages += it.joinToString(" ") to StageState.SKIPPED
            }
        } else {
            val failedIdx = r.failedStep?.let { r.commands.indexOf(it) } ?: -1
            for ((idx, step) in r.commands.withIndex()) {
                val label = step.joinToString(" ")
                val state = when {
                    // No failedStep means either everything passed, or the run never started
                    // chr (patch error, missing chromia.yml). In the latter case status is
                    // FAILED/EXPECTED_FAIL with durationMs == 0 — show the first stage as the
                    // failure point and the rest as skipped.
                    failedIdx < 0 -> when (r.status) {
                        Status.PASSED -> StageState.PASS
                        Status.EXPECTED_FAIL -> if (idx == 0) StageState.XFAIL else StageState.SKIPPED
                        Status.FAILED -> if (idx == 0) StageState.FAIL else StageState.SKIPPED
                    }

                    idx < failedIdx -> StageState.PASS
                    idx == failedIdx -> if (r.status == Status.EXPECTED_FAIL) StageState.XFAIL else StageState.FAIL
                    else -> StageState.SKIPPED
                }

                stages += label to state
            }
        }

        // Compact mode: each stage is a colored dot. Full label + state surface via a
        // CSS-driven tooltip (`data-tip`) that fires immediately on :hover — the native
        // `title` attribute carries a multi-hundred-millisecond browser delay that's
        // noticeably slow next to GitLab-style pipeline indicators.
        stages.forEachIndexed { i, (label, state) ->
            if (i > 0) span(classes = "sep") { +"·" }

            span(classes = "stage stage-${state.cssClass()}") {
                attributes["data-tip"] = "$label — ${state.label()}"
                unsafe { +state.iconSvg() }
            }
        }
    }
}

// Inline-SVG icons in the spirit of heroicons-mini. Vector glyphs are flatter and crisper
// at small sizes than Unicode check/cross codepoints.
private fun StageState.iconSvg(): String = when (this) {
    StageState.PASS -> """<svg class="stage-glyph" viewBox="0 0 20 20" aria-hidden="true"><path fill="currentColor" d="M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 1 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z"/></svg>"""
    StageState.FAIL -> """<svg class="stage-glyph" viewBox="0 0 20 20" aria-hidden="true"><path fill="currentColor" d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z"/></svg>"""
    StageState.XFAIL -> """<svg class="stage-glyph" viewBox="0 0 20 20" aria-hidden="true"><path fill="currentColor" d="M4 10a.75.75 0 0 1 .75-.75h10.5a.75.75 0 0 1 0 1.5H4.75A.75.75 0 0 1 4 10Z"/></svg>"""
    StageState.SKIPPED -> """<svg class="stage-glyph" viewBox="0 0 20 20" aria-hidden="true"><circle cx="10" cy="10" r="2" fill="currentColor"/></svg>"""
}

private fun StageState.cssClass(): String = when (this) {
    StageState.PASS -> "pass"
    StageState.FAIL -> "fail"
    StageState.XFAIL -> "xfail"
    StageState.SKIPPED -> "skipped"
}

private fun StageState.label(): String = when (this) {
    StageState.PASS -> "passed"
    StageState.FAIL -> "failed"
    StageState.XFAIL -> "expected fail"
    StageState.SKIPPED -> "skipped"
}

private fun FlowContent.renderMethodology() = renderSection("methodology") {
    ul(classes = "method") {
        li {
            +"Each project is cloned into "
            code { +"regression/workdir/<name>" }
            +" at the configured ref (default branch if unset), then compiled with "
            code { +"chr <command>" }
            +" against the local Rell build."
        }
    }
}

private fun Status.cssClass(): String = when (this) {
    Status.PASSED -> "pass"
    Status.FAILED -> "fail"
    Status.EXPECTED_FAIL -> "xfail"
    Status.CLONE_FAILED -> "clone"
}

private fun Status.label(): String = when (this) {
    Status.PASSED -> "passed"
    Status.FAILED -> "failed"
    Status.EXPECTED_FAIL -> "expected fail"
    Status.CLONE_FAILED -> "clone failed"
}

@Suppress("HttpUrlsUsage")
private fun shortenUrl(url: String): String = url
    .removeSuffix(".git")
    .removePrefix("https://")
    .removePrefix("http://")

@Suppress("CssUnresolvedCustomProperty", "CssUnusedSymbol", "CssNoGenericFontName")
@Language("CSS")
private val REGRESSION_CSS = """
table.regression { border-collapse: collapse; width: 100%; }
table.regression td.name { font-family: var(--sans); font-weight: 500; min-width: 200px; max-width: 320px; }
table.regression td.name .row-notes {
  font-size: .72rem; color: var(--muted); font-family: var(--sans);
  font-weight: 400; margin-top: .2rem; line-height: 1.35;
}
table.regression td.source {
  font-family: var(--mono); font-size: .76rem; color: var(--ink-soft);
  word-break: break-all; max-width: 360px;
}
table.regression td.source .ref { color: var(--muted); font-size: .72rem; }
table.regression td.source code { font-size: .72rem; color: var(--muted); background: var(--bg-alt); padding: 0 4px; border-radius: 2px; }
table.regression td.pipeline-cell { font-family: var(--mono); font-size: .76rem; color: var(--ink-soft); }

table.regression tr.log-row > td { border-top: 0; padding-top: 0; }
table.regression td.log-cell { padding: 0 .8rem .8rem .8rem; }
table.regression td.log-cell details > summary {
  cursor: pointer; list-style: none;
  font-family: var(--mono); font-size: .7rem; color: var(--accent); font-weight: 600;
}
table.regression td.log-cell details > summary::-webkit-details-marker { display: none; }
table.regression td.log-cell details > summary::before { content: "▸ "; color: var(--faint); font-weight: 400; }
table.regression td.log-cell details[open] > summary::before { content: "▾ "; }
table.regression td.log-cell .log-excerpt {
  margin-top: .5rem; padding: .6rem .8rem;
  background: var(--bg-alt); border-left: 2px solid var(--accent);
  font-family: var(--mono); font-size: .72rem; color: var(--ink);
  white-space: pre-wrap; word-break: break-word; line-height: 1.45;
  max-height: 320px; overflow: auto;
}
table.regression td.log-cell .log-link { margin-top: .35rem; font-size: .7rem; }

table.regression tr.row-fail { background: rgba(193,68,14,0.04); }
table.regression tr.log-row.row-fail { background: rgba(193,68,14,0.04); }

.pipeline {
  display: inline-flex; align-items: center; gap: 3px;
  vertical-align: middle;
}
.pipeline .stage {
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px; border-radius: 50%; border: 1px solid var(--rule);
  line-height: 1; cursor: help; position: relative;
}
.pipeline .stage .stage-glyph { width: 11px; height: 11px; display: block; }
/* Instant CSS tooltip — no native title attribute delay. Reveals on hover/focus.
   The stage sits in position: relative so the ::after positions over its dot. */
.pipeline .stage[data-tip]::after {
  content: attr(data-tip);
  position: absolute; bottom: calc(100% + 6px); left: 50%; transform: translateX(-50%);
  padding: 3px 7px; border-radius: 3px;
  background: var(--ink); color: var(--bg);
  font-family: var(--mono); font-size: .7rem; font-weight: 500;
  white-space: nowrap; pointer-events: none;
  opacity: 0; transition: opacity 60ms linear;
  z-index: 10;
}
.pipeline .stage[data-tip]:hover::after,
.pipeline .stage[data-tip]:focus::after { opacity: 1; }
.pipeline .stage-pass    { color: #1F6B4A; border-color: rgba(31,107,74,0.4); background: rgba(31,107,74,0.10); }
.pipeline .stage-fail    { color: var(--accent); border-color: rgba(193,68,14,0.4); background: rgba(193,68,14,0.12); }
.pipeline .stage-xfail   { color: var(--muted); border-color: var(--rule); background: var(--bg-alt); }
.pipeline .stage-skipped { color: var(--faint); border-color: var(--faint); background: transparent; }
.pipeline .sep { color: var(--faint); font-size: .6rem; line-height: 1; }

.badge {
  display: inline-block; padding: 2px 8px; border-radius: 2px;
  font-family: var(--mono); font-size: .68rem; font-weight: 600;
  text-transform: uppercase; letter-spacing: .14em;
  border: 1px solid var(--rule);
}
.badge-pass  { color: #1F6B4A; border-color: rgba(31,107,74,0.4); background: rgba(31,107,74,0.06); }
.badge-fail  { color: var(--accent); border-color: rgba(193,68,14,0.4); background: rgba(193,68,14,0.08); }
.badge-xfail { color: var(--muted); border-color: var(--rule); background: var(--bg-alt); }
.badge-clone { color: var(--muted); border-color: var(--rule); background: transparent; }

ul.method { padding-left: 1.2rem; color: var(--ink-soft); font-size: .88rem; }
ul.method li { margin-bottom: .55rem; line-height: 1.55; }
ul.method code { font-family: var(--mono); font-size: .8rem; background: var(--bg-alt); padding: 1px 5px; border-radius: 2px; color: var(--ink); }

/* Flavor switcher: pure-CSS radio tabbar between Interpreter and Truffle runs.
   Inputs live as direct siblings of the panels so `:checked ~ .flavor-panel-X` can
   show/hide without JavaScript. The visible labels in .flavor-switch sit between the
   inputs and the panels and pick up the same selectors to style themselves. */
.flavor-radio { position: absolute; left: -9999px; width: 0; height: 0; opacity: 0; }
.flavor-switch {
  display: inline-flex; margin: 0 0 1.4rem 0;
  border: 1px solid var(--rule); border-radius: 3px; overflow: hidden;
  background: var(--bg-alt);
}
.flavor-switch .flavor-tab {
  padding: 6px 16px; cursor: pointer; user-select: none;
  font-family: var(--mono); font-size: .72rem; font-weight: 600;
  text-transform: uppercase; letter-spacing: .14em; color: var(--muted);
  border-right: 1px solid var(--rule);
  transition: background-color 80ms linear, color 80ms linear;
}
.flavor-switch .flavor-tab:last-child { border-right: none; }
.flavor-switch .flavor-tab:hover { color: var(--ink); }
.flavor-radio:focus-visible + .flavor-radio + .flavor-switch .flavor-tab,
.flavor-radio:focus-visible + .flavor-switch .flavor-tab { outline: 2px solid var(--accent); outline-offset: -2px; }

.flavor-panel { display: none; }
#flavor-interpreter:checked ~ .flavor-panel-interpreter { display: block; }
#flavor-truffle:checked     ~ .flavor-panel-truffle     { display: block; }
#flavor-interpreter:checked ~ .flavor-switch label[for="flavor-interpreter"],
#flavor-truffle:checked     ~ .flavor-switch label[for="flavor-truffle"] {
  background: var(--ink); color: var(--bg);
}
""".trimIndent()
