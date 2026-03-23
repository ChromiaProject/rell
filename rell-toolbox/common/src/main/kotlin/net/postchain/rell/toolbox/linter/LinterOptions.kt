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
) {
    fun updateOptionsFromFile(configFile: File) {
        EditorConfigParser.parse(configFile)?.let {
            it.sections.forEach { section ->
                section.properties.forEach { property ->
                    when (property.key) {
                        "rule_naming_convention" -> {
                            ruleNamingConvention = parseBoolean(property.value)
                        }

                        "rule_import_from_non_module" -> {
                            ruleImportFromNonModule = parseBoolean(property.value)
                        }

                        "rule_quote_format" -> {
                            ruleQuoteFormat = when (property.value.sourceValue.trim()) {
                                "double" -> Quote.DOUBLE
                                "single" -> Quote.SINGLE
                                else -> null
                            }
                        }

                        "rule_formatter" -> {
                            ruleFormatter = parseBoolean(property.value)
                        }

                        "rule_constant_detection" -> {
                            ruleConstantDetection = parseBoolean(property.value)
                        }

                        "rule_unused_variable" -> {
                            ruleUnusedVariable = parseBoolean(property.value)
                        }
                        "rule_outer_join_cartesian_product" -> {
                            ruleOuterJoinCartesianProduct = parseBoolean(property.value)
                        }
                    }
                }
            }
            enable()
        } ?: disable()
    }

    private fun parseBoolean(value: Property): Boolean? {
        return when (value.sourceValue.trim()) {
            "true" -> true
            "false" -> false
            else -> null
        }
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
