package net.postchain.rell.toolbox.linter.rules

import assertk.Assert
import assertk.assertions.support.expected
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue

fun Assert<LinterIssue>.matches(
    expectedLine: Int,
    expectedColumn: Int,
    expectedRuleId: String,
    expectedMessage: String,
    fix: LinterFix? = null
) = given { actual ->
    if (actualMatchesExpected(actual, expectedRuleId, expectedMessage, expectedLine, expectedColumn, fix)) {
        return
    }
    val expectedAndActualData = "line=$expectedLine, column=$expectedColumn, rule-id=$expectedRuleId, " +
        "message=$expectedMessage fix=$fix but was line=${actual.ctx.start.line}, " +
        "column=${actual.ctx.start.charPositionInLine + 1}, rule-id=${actual.ruleId}, " +
        "message=${actual.message}, fix=${actual.fix()}"
    expected(expectedAndActualData)
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
