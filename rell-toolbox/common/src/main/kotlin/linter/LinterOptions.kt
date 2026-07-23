/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.editorconfig.EditorConfigParser
import org.ec4j.core.model.Property
import java.io.File

data class LinterOptions(
    var enabled: Boolean = false,

    var ruleQuoteFormat: Quote? = null,
    var ruleNamingConvention: Boolean? = null,
    var ruleImportFromNonModule: Boolean? = null,
    var ruleFormatter: Boolean? = null,
    var ruleConstantDetection: Boolean? = null,
    var ruleUnusedVariable: Boolean? = null,
    var ruleOuterJoinCartesianProduct: Boolean? = null,
    var ruleReplaceIfWithWhen: Boolean? = null,
    var ruleSimplifyBooleanReturn: Boolean? = null,
    var ruleRedundantBooleanComparison: Boolean? = null,
    var ruleSimplifyNullableIf: Boolean? = null,
    var rulePreferVal: Boolean? = null,
    var rulePreferEmpty: Boolean? = null,
    var ruleAtCardinalityMisuse: Boolean? = null,
) {
    fun updateOptionsFromFile(configFile: File) {
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
                        "rule_prefer_val" -> rulePreferVal = parseBoolean(value)
                        "rule_prefer_empty" -> rulePreferEmpty = parseBoolean(value)
                        "rule_at_cardinality_misuse" -> ruleAtCardinalityMisuse = parseBoolean(value)
                    }
                }
            }

            enable()
        } ?: disable()
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
