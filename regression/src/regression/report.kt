/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.Context
import kotlinx.html.FlowContent
import kotlinx.html.TBODY
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.code
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.pre
import kotlinx.html.span
import kotlinx.html.stream.appendHTML
import kotlinx.html.style
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import net.postchain.rell.performance.report.BASE_CSS
import net.postchain.rell.performance.report.HostInfo
import net.postchain.rell.performance.report.JvmInfo
import net.postchain.rell.performance.report.dlRow
import net.postchain.rell.performance.report.formatRoot
import net.postchain.rell.performance.report.hostBlock
import net.postchain.rell.performance.report.jvmBlock
import net.postchain.rell.performance.report.linkWebFonts
import net.postchain.rell.performance.report.metric
import net.postchain.rell.performance.report.renderColophon
import net.postchain.rell.performance.report.renderDocHead
import net.postchain.rell.performance.report.renderSection
import net.postchain.rell.performance.report.sysinfoBlock
import net.postchain.rell.performance.report.titleWithTimestamp
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.writeText

class ReportCommand : RegressionSubcommand("report") {
    override fun help(context: Context) =
        "Render reports/report.html from reports/results.json. Run :regressionCompile first to produce the JSON."

    override fun run() {
        val resultsFile = reportsDir / "results.json"

        require(resultsFile.exists()) {
            "No results.json at $resultsFile — run `:regressionCompile` (or `:regression`) first."
        }

        val results: ResultsFile = regressionMapper.readValue(resultsFile.inputStream())
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
                    renderSummary(results)
                    renderProjectSections(results)
                    renderMethodology()
                }
                // Total compile time goes here, not in the key metrics — reviewers don't act on it,
                // they act on the failed/expected-fail/passed counts above.
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

private fun FlowContent.renderSummary(results: ResultsFile) = renderSection("summary") {
    val counts = results.results.groupingBy { it.status }.eachCount()
    val passed = counts[Status.PASSED] ?: 0
    val failed = counts[Status.FAILED] ?: 0
    val expectedFail = counts[Status.EXPECTED_FAIL] ?: 0
    val total = results.results.size
    val rate = if (total > 0) 100.0 * passed / total else 0.0

    div(classes = "metrics") {
        metric("Projects", total.toString(), "", "in scope")
        metric("Passed", passed.toString(), "", "compile ok")
        metric("Failed", failed.toString(), "", "non-zero chr exit")
        metric("Expected fail", expectedFail.toString(), "", "historical / FT3-era")
        metric("Pass rate", "%.0f".formatRoot(rate), "%", "of total")
    }
}

/**
 * Renders one `renderSection` per cohort (public, private, …) so the public results stay
 * visually distinct from any private overlay. If a project came from a config file without a
 * stamped cohort (older results.json) it lands in a `projects` section so the report still
 * renders cleanly.
 */
private fun FlowContent.renderProjectSections(results: ResultsFile) {
    // Sort: failed first (what reviewers actually care about), then everything else in original order.
    val priority = mapOf(
        Status.FAILED to 0,
        Status.CLONE_FAILED to 1,
        Status.EXPECTED_FAIL to 2,
        Status.PASSED to 3,
    )
    val byCohort = results.results
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
    strap = "${rows.size} project${if (rows.size == 1) "" else "s"} · click a failed row " +
        "to read the last few lines of chr stderr.",
) {
    div(classes = "table-scroll") {
        table(classes = "regression") {
            thead {
                tr {
                    th { +"Project" }
                    th { +"Status" }
                    th(classes = "num") { +"Duration" }
                    th { +"Source" }
                    th { +"Detail" }
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
        td(classes = "num") {
            r.durationMs?.let { +"%.1f s".formatRoot(it / 1000.0) } ?: run { +"—" }
        }
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
        td(classes = "detail") {
            if (r.errorSummary != null) {
                details {
                    summary {
                        +"chr exit ${r.exitCode}${if (r.timedOut) " · timed out" else ""}"
                        r.failedStep?.let { step ->
                            +" "
                            span(classes = "failed-step") { +"@ chr ${step.joinToString(" ")}" }
                        }
                    }
                    pre(classes = "log-excerpt") { +r.errorSummary }
                    r.logRelPath?.let {
                        div(classes = "log-link") {
                            a(href = it) { +"full log" }
                        }
                    }
                }
            } else if (r.status == Status.PASSED) {
                +"chr exit 0"
            } else {
                +"—"
            }
        }
    }
}

private fun FlowContent.renderStatusBadge(r: CompileResult) {
    span(classes = "badge badge-${r.status.cssClass()}") {
        +r.status.label()
    }
}

private fun FlowContent.renderMethodology() = renderSection("methodology") {
    ul(classes = "method") {
        li {
            +"Each project is shallow-cloned into "
            code { +"regression/workdir/<name>" }
            +" at the configured ref (default branch if unset), then compiled in place with "
            code { +"chr <command>" }
            +" against the local Rell build (jars synced into the chromia-cli distribution by "
            code { +"work/local-chr.sh" }
            +")."
        }
        li {
            +"A row reports "
            code { +"FAILED" }
            +" only when "
            code { +"expectedFailure" }
            +" is not set in the project's JSON entry. Historical / FT3-era projects are marked "
            code { +"expectedFailure: true" }
            +" so they don't show up as regressions; if such a project starts compiling again it flips to "
            code { +"UNEXPECTED_PASS" }
            +"."
        }
        li {
            +"Per-project compile timeout: "
            code { +"20 min" }
            +". Bootstrap timeout (one-time chromia-cli build): "
            code { +"60 min" }
            +"."
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
table.regression td.detail { font-family: var(--mono); font-size: .76rem; color: var(--ink-soft); }
table.regression td.detail details > summary {
  cursor: pointer; color: var(--accent); font-weight: 600;
  list-style: none;
}
table.regression td.detail details > summary::-webkit-details-marker { display: none; }
table.regression td.detail details > summary::before { content: "▸ "; color: var(--faint); font-weight: 400; }
table.regression td.detail details[open] > summary::before { content: "▾ "; }
table.regression td.detail .log-excerpt {
  margin-top: .5rem; padding: .6rem .8rem;
  background: var(--bg-alt); border-left: 2px solid var(--accent);
  font-family: var(--mono); font-size: .72rem; color: var(--ink);
  white-space: pre-wrap; word-break: break-word; line-height: 1.45;
  max-height: 320px; overflow: auto;
}
table.regression td.detail .log-link { margin-top: .35rem; font-size: .7rem; }

table.regression tr.row-fail { background: rgba(193,68,14,0.04); }

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

table.regression .failed-step {
  color: var(--muted); font-family: var(--mono); font-size: .68rem;
  font-weight: 500; letter-spacing: .02em; margin-left: .35rem;
}

ul.method { padding-left: 1.2rem; color: var(--ink-soft); font-size: .88rem; }
ul.method li { margin-bottom: .55rem; line-height: 1.55; }
ul.method code { font-family: var(--mono); font-size: .8rem; background: var(--bg-alt); padding: 1px 5px; border-radius: 2px; color: var(--ink); }
""".trimIndent()
