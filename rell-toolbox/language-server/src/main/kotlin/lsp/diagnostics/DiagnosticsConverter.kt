/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.diagnostics

import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.indexer.RellIssueSeverity
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.math.max

object DiagnosticsConverter {

    fun toDiagnostics(issues: List<RellIssue>): List<Diagnostic> {
        return issues.map(::toDiagnostic)
    }

    private fun toDiagnostic(issue: RellIssue): Diagnostic {
        val result = Diagnostic()
        result.code = Either.forLeft(issue.code)
        result.message = issue.message
        result.severity = toDiagnosticSeverity(issue.severity)
        result.range = extractRange(issue)
        return result
    }

    private fun extractRange(issue: RellIssue): Range {
        // line and column numbers in LSP are 0-based
        val start = Position(max(0, issue.line - 1), max(0, issue.column - 1))
        val end = Position(max(0, issue.line - 1), max(0, issue.column - 1))
        return Range(start, end)
    }

    private fun toDiagnosticSeverity(severity: RellIssueSeverity): DiagnosticSeverity {
        return when (severity) {
            RellIssueSeverity.ERROR -> DiagnosticSeverity.Error
            RellIssueSeverity.WARNING -> DiagnosticSeverity.Warning
        }
    }
}
