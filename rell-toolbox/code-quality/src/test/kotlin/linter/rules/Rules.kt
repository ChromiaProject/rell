/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.Assert
import assertk.assertions.support.expected
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue

fun Assert<LinterIssue>.matches(
    expectedLine: Int,
    expectedColumn: Int,
    expectedRuleId: String,
    expectedMessage: String,
    fix: LinterFix? = null
): Unit = given { actual ->
    if (actualMatchesExpected(actual, expectedRuleId, expectedMessage, expectedLine, expectedColumn, fix)) {
        return
    }
    val expectedAndActualData = "line=$expectedLine, column=$expectedColumn, rule-id=$expectedRuleId, " +
        "message=$expectedMessage fix=$fix but was line=${actual.ctx.start.line}, " +
        "column=${actual.ctx.start.charPositionInLine + 1}, rule-id=${actual.ruleId}, " +
        "message=${actual.message}, fix=${actual.fix()}"
    expected(expectedAndActualData)
}

/** Asserts the editor-highlight span the issue reports, 1-based, end-exclusive. */
fun Assert<LinterIssue>.highlightsRange(
    expectedLine: Int,
    expectedColumn: Int,
    expectedEndLine: Int,
    expectedEndColumn: Int,
): Unit = given { actual ->
    val issue = RellIssue.fromLinterIssue(actual)
    if (issue.line == expectedLine && issue.column == expectedColumn &&
        issue.endLine == expectedEndLine && issue.endColumn == expectedEndColumn
    ) {
        return
    }
    expected(
        "highlight $expectedLine:$expectedColumn..$expectedEndLine:$expectedEndColumn " +
            "but was ${issue.line}:${issue.column}..${issue.endLine}:${issue.endColumn}"
    )
}

private fun actualMatchesExpected(
    actual: LinterIssue,
    expectedRuleId: String,
    expectedMessage: String,
    expectedLine: Int,
    expectedColumn: Int,
    fix: LinterFix?
) = actual.ruleId == expectedRuleId &&
    actual.message == expectedMessage &&
    actual.ctx.start.line == expectedLine &&
    actual.ctx.start.charPositionInLine + 1 == expectedColumn &&
    actual.fix() == fix
