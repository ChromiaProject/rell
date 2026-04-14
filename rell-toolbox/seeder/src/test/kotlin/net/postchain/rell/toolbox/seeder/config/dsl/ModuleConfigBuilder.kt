/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.dsl

import net.postchain.rell.toolbox.seeder.config.EntityConfig
import net.postchain.rell.toolbox.seeder.config.ModuleConfig

class ModuleConfigBuilder {
    private var moduleName: String? = null
    private val entityConfigs = mutableMapOf<String, EntityConfig>()

    fun module(name: String) {
        moduleName = name
    }

    fun entity(name: String, block: EntityConfigBuilder.() -> Unit) {
        val builder = EntityConfigBuilder(name)
        builder.block()
        entityConfigs[name] = builder.build()
    }

    fun build(): ModuleConfig {
        check(moduleName != null) { "moduleName must be set" }
        return ModuleConfig(moduleName!!, entityConfigs)
    }
}
