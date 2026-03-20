package net.postchain.rell.toolbox.seeder.config.dsl

import net.postchain.rell.toolbox.seeder.Attribute
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.Distribution
import net.postchain.rell.toolbox.seeder.config.EntityConfig

class EntityConfigBuilder(private val name: String) {
    private val attributeConfigs = mutableMapOf<String, AttributeConfig>()
    private var count = DEFAULT_COUNT

    fun attributes(block: AttributesConfigBuilder.() -> Unit) {
        val builder = AttributesConfigBuilder(attributeConfigs)
        builder.block()
    }

    fun attribute(name: String, config: AttributeConfig) {
        attributeConfigs[name] = config
    }

    fun predefined(field: String, values: List<Any>, distribution: Distribution = Distribution.SEQUENTIAL) {
        attributeConfigs[field] = AttributeConfig.PredefinedValues(values, distribution)
    }

    fun range(field: String, min: Number, max: Number) {
        attributeConfigs[field] = AttributeConfig.Range(min, max)
    }

    fun text(field: String, min: Int? = null, max: Int? = null) {
        attributeConfigs[field] = AttributeConfig.TextConfig(min, max)
    }

    fun byteArray(field: String, size: Int? = null) {
        attributeConfigs[field] = AttributeConfig.ByteArrayConfig(size)
    }

    fun dataPattern(field: String, name: String) {
        attributeConfigs[field] = AttributeConfig.DataPatternConfig(name)
    }

    fun custom(field: String, generator: (Attribute) -> Any?) {
        attributeConfigs[field] = AttributeConfig.CustomGenerator(generator)
    }

    fun count(count: Int) {
        this.count = count
    }

    fun build(): EntityConfig {
        return EntityConfig(name, attributeConfigs, count)
    }

    companion object {
        const val DEFAULT_COUNT = 10
    }
}
