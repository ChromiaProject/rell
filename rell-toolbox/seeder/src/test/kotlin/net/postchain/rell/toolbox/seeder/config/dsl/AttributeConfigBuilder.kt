/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.dsl

import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.Distribution

class AttributesConfigBuilder(private val attributeConfigs: MutableMap<String, AttributeConfig>) {

    fun attribute(name: String, block: AttributeConfigBuilder.() -> Unit) {
        val builder = AttributeConfigBuilder(name)
        builder.block()
        attributeConfigs[name] = builder.build()
    }
}

class AttributeConfigBuilder(private val name: String) {
    private var generatorType: String? = null
    private var min: Number? = null
    private var max: Number? = null
    private var values: List<Any>? = null
    private var size: Int? = null
    private var distribution: Distribution? = null

    fun generator(type: String) {
        this.generatorType = type
    }

    fun min(value: Number) {
        this.min = value
    }

    fun max(value: Number) {
        this.max = value
    }

    fun size(value: Int) {
        this.size = value
    }

    fun values(valuesList: List<Any>) {
        this.values = valuesList
    }

    fun distribution(dist: Distribution) {
        this.distribution = dist
    }

    fun build(): AttributeConfig {
        return when (generatorType) {
            "text" -> AttributeConfig.TextConfig(min?.toInt(), max?.toInt())
            "byte_array" -> AttributeConfig.ByteArrayConfig(size)
            "range" -> {
                requireNotNull(min) { "min value is required for range generator" }
                requireNotNull(max) { "max value is required for range generator" }
                AttributeConfig.Range(min!!, max!!)
            }
            "predefined" -> {
                requireNotNull(values) { "values are required for predefined generator" }
                AttributeConfig.PredefinedValues(values!!, distribution)
            }
            else -> AttributeConfig.DataPatternConfig(generatorType ?: "text")
        }
    }
}
