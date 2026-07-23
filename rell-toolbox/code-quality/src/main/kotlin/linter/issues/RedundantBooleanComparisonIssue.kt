/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue

class RedundantBooleanComparisonIssue(
    binaryCtx: RellParser.BinaryExprContext,
    ruleId: String,
    message: String,
) : LinterIssue(binaryCtx, ruleId, message) {

    // Diagnostic-only. A purely syntactic rule cannot distinguish a plain `boolean` from a
    // `boolean?` operand, and the naive rewrite is unsafe for the nullable case: `x == false`
    // -> `not x` fails to compile (`not` requires a non-null boolean), and `x == true` -> `x`
    // silently changes semantics (`== true` collapses null to false; bare `x` propagates null).
    // A type-aware auto-fix belongs to a later wave.
    override fun fix(): LinterFix? = null
}
