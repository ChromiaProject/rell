/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.editorconfig.EditorConfigParser
import org.ec4j.core.model.Property
import java.io.File

/**
 * Inspections are on out of the box, each at the severity its issues declare. The `.rell_lint` file
 * exists to tune or switch inspections off, not to switch them on.
 */
data class LinterOptions(
    var enabled: Boolean = true,

    // No sensible default - a project has to choose single or double quotes - so this stays opt-in.
    var ruleQuoteFormat: Quote? = null,
    var ruleNamingConvention: Boolean? = true,
    var ruleImportFromNonModule: Boolean? = true,
    // Gates the formatter rather than an inspection, so it stays opt-in.
    var ruleFormatter: Boolean? = null,
    var ruleConstantDetection: Boolean? = true,
    var ruleUnusedVariable: Boolean? = true,
    var ruleOuterJoinCartesianProduct: Boolean? = true,
    var ruleReplaceIfWithWhen: Boolean? = true,
    var ruleSimplifyBooleanReturn: Boolean? = true,
    var ruleRedundantBooleanComparison: Boolean? = true,
    var ruleSimplifyNullableIf: Boolean? = true,
    var rulePreferEmpty: Boolean? = true,
    var ruleAtCardinalityMisuse: Boolean? = true,
) {
    fun updateOptionsFromFile(configFile: File) {
        // A missing or unreadable config leaves the built-in defaults in place, so inspections stay on.
        EditorConfigParser.parse(configFile)?.let {
            for (section in it.sections) {
                for ((key, value) in section.properties) {
                    when (key) {
                        "rule_naming_convention" -> ruleNamingConvention = parseBoolean(value)
                        "rule_import_from_non_module" -> ruleImportFromNonModule = parseBoolean(value)

                        "rule_quote_format" -> ruleQuoteFormat = when (value.sourceValue.trim()) {
                            "double" -> Quote.DOUBLE
                            "single" -> Quote.SINGLE
                            else -> null
                        }

                        "rule_formatter" -> ruleFormatter = parseBoolean(value)
                        "rule_constant_detection" -> ruleConstantDetection = parseBoolean(value)
                        "rule_unused_variable" -> ruleUnusedVariable = parseBoolean(value)
                        "rule_outer_join_cartesian_product" -> ruleOuterJoinCartesianProduct = parseBoolean(value)
                        "rule_replace_if_with_when" -> ruleReplaceIfWithWhen = parseBoolean(value)
                        "rule_simplify_boolean_return" -> ruleSimplifyBooleanReturn = parseBoolean(value)
                        "rule_redundant_boolean_comparison" -> ruleRedundantBooleanComparison = parseBoolean(value)
                        "rule_simplify_nullable_if" -> ruleSimplifyNullableIf = parseBoolean(value)
                        "rule_prefer_empty" -> rulePreferEmpty = parseBoolean(value)
                        "rule_at_cardinality_misuse" -> ruleAtCardinalityMisuse = parseBoolean(value)
                    }
                }
            }

            enable()
        }
    }

    private fun parseBoolean(value: Property): Boolean? = when (value.sourceValue.trim()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    fun disable() {
        enabled = false
    }

    fun enable() {
        enabled = true
    }

    companion object {
        const val CONFIG_FILE_NAME = ".rell_lint"
    }
}

enum class Quote(val literal: String) {
    SINGLE("'"),
    DOUBLE("\""),
}
