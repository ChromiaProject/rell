/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.serializer

import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.ModuleConfig

class ModuleConfigSerializer {

    fun serialize(config: ModuleConfig): String {
        return buildString {
            appendLine("module: ${config.moduleName}")

            config.entityConfigs.forEach { entity ->
                appendLine("")
                appendLine("${entity.value.name}:")
                appendLine("  count: ${entity.value.count}")
                if (entity.value.attributes.isNotEmpty()) {
                    appendLine("  attributes:")
                    entity.value.attributes.forEach { field ->
                        val fieldValue = field.value
                        appendLine("    ${field.key}:")
                        when (fieldValue) {
                            is AttributeConfig.PredefinedValues -> {
                                appendLine("      generator: predefined")
                                appendLine(
                                    "      values: ${fieldValue.values.joinToString(", ", prefix = "[", postfix = "]")}"
                                )
                                fieldValue.distribution?.let {
                                    appendLine("      distribution: ${it.name.lowercase()}")
                                }
                            }

                            is AttributeConfig.Range -> {
                                appendLine("      generator: range")
                                appendLine("      min: ${fieldValue.min}")
                                appendLine("      max: ${fieldValue.max}")
                            }

                            is AttributeConfig.TextConfig -> {
                                appendLine("      generator: text")
                                fieldValue.min?.let { min -> appendLine("      min: $min") }
                                fieldValue.max?.let { max -> appendLine("      max: $max") }
                            }

                            is AttributeConfig.ByteArrayConfig -> {
                                appendLine("      generator: byte_array")
                                fieldValue.size?.let { size -> appendLine("      size: $size") }
                            }

                            is AttributeConfig.DataPatternConfig -> {
                                appendLine("      generator: ${fieldValue.pattern}")
                            }

                            else -> throw IllegalArgumentException(
                                "Unsupported field value type: ${fieldValue.javaClass.name}"
                            )
                        }
                    }
                }
            }
        }
    }
}
