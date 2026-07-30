/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.serializer

import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.ModuleConfig

internal class ModuleConfigSerializer {

    fun serialize(config: ModuleConfig): String {
        return buildString {
            appendLine("module: ${config.moduleName}")

            for ((_, entityConfig) in config.entityConfigs) {
                appendLine("")
                appendLine("${entityConfig.name}:")
                appendLine("  count: ${entityConfig.count}")
                if (entityConfig.attributes.isNotEmpty()) {
                    appendLine("  attributes:")
                    for ((key, attributeConfig) in entityConfig.attributes) {
                        appendLine("    $key:")

                        when (attributeConfig) {
                            is AttributeConfig.PredefinedValues -> {
                                appendLine("      generator: predefined")
                                appendLine(
                                    "      values: ${attributeConfig.values.joinToString(", ", prefix = "[", postfix = "]")}"
                                )
                                attributeConfig.distribution?.let {
                                    appendLine("      distribution: ${it.name.lowercase()}")
                                }
                            }

                            is AttributeConfig.Range -> {
                                appendLine("      generator: range")
                                appendLine("      min: ${attributeConfig.min}")
                                appendLine("      max: ${attributeConfig.max}")
                            }

                            is AttributeConfig.TextConfig -> {
                                appendLine("      generator: text")
                                attributeConfig.min?.let { min -> appendLine("      min: $min") }
                                attributeConfig.max?.let { max -> appendLine("      max: $max") }
                            }

                            is AttributeConfig.ByteArrayConfig -> {
                                appendLine("      generator: byte_array")
                                attributeConfig.size?.let { size -> appendLine("      size: $size") }
                            }

                            is AttributeConfig.DataPatternConfig -> appendLine("      generator: ${attributeConfig.pattern}")

                            else -> throw IllegalArgumentException(
                                "Unsupported field value type: ${attributeConfig.javaClass.name}"
                            )
                        }
                    }
                }
            }
        }
    }
}
