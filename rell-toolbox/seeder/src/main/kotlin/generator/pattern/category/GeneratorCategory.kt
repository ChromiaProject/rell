/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.toolbox.seeder.generator.DataGeneratorContext
import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry
import net.postchain.rell.toolbox.seeder.generator.pattern.SimpleDataPatternGenerator

abstract class GeneratorCategory(
    protected val registry: GeneratorRegistry
) {
    abstract fun register()

    protected fun register(
        identifier: String,
        type: R_Type = R_TextType,
        generatorFn: (ctx: DataGeneratorContext) -> Any
    ) {
        registry.register(
            SimpleDataPatternGenerator(
                identifier = identifier,
                type = type,
                generatorFn = generatorFn
            )
        )
    }

    protected fun selectGenerator(ctx: DataGeneratorContext, generatorFn: () -> Any, uniqueGenerator: () -> Any): Any {
        return if (ctx.isAttributeUnique()) {
            uniqueGenerator()
        } else {
            generatorFn()
        }
    }
}
