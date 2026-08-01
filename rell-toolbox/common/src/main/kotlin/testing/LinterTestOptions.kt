/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.testing

import net.postchain.rell.toolbox.linter.LinterOptions

/**
 * Linter options with every inspection switched off, so a test can exercise a single rule in
 * isolation. Production defaults have inspections on, which means a test that only enables the rule
 * under test would otherwise also collect issues from all the others.
 *
 * ```
 * testLinterOptions { rulePreferEmpty = true }
 * testLinterOptions(enabled = false) { rulePreferEmpty = true }
 * ```
 */
fun testLinterOptions(enabled: Boolean = true, configure: LinterOptions.() -> Unit = {}): LinterOptions =
    LinterOptions(
        enabled = enabled,
        ruleQuoteFormat = null,
        ruleNamingConvention = false,
        ruleImportFromNonModule = false,
        ruleFormatter = false,
        ruleConstantDetection = false,
        ruleUnusedVariable = false,
        ruleOuterJoinCartesianProduct = false,
        ruleReplaceIfWithWhen = false,
        ruleSimplifyBooleanReturn = false,
        ruleRedundantBooleanComparison = false,
        ruleSimplifyNullableIf = false,
        rulePreferNullCheckOperator = false,
        rulePreferEmpty = false,
        ruleAtCardinalityMisuse = false,
    ).apply(configure)
