package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class MeasurementGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("measurement.height") {
            selectGenerator(it, it.faker.measurement::height, it.faker.measurement.unique::height)
        }
        register("measurement.weight") {
            selectGenerator(it, it.faker.measurement::weight, it.faker.measurement.unique::weight)
        }
        register("measurement.volume") {
            selectGenerator(it, it.faker.measurement::volume, it.faker.measurement.unique::volume)
        }
        register("measurement.length") {
            selectGenerator(it, it.faker.measurement::length, it.faker.measurement.unique::length)
        }
        register("measurement.metric_height") {
            selectGenerator(it, it.faker.measurement::metricHeight, it.faker.measurement.unique::metricHeight)
        }
        register("measurement.metric_weight") {
            selectGenerator(it, it.faker.measurement::metricWeight, it.faker.measurement.unique::metricWeight)
        }
        register("measurement.metric_volume") {
            selectGenerator(it, it.faker.measurement::metricVolume, it.faker.measurement.unique::metricVolume)
        }
        register("measurement.metric_length") {
            selectGenerator(it, it.faker.measurement::metricLength, it.faker.measurement.unique::metricLength)
        }
    }
}
