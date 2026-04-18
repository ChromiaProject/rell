/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern

import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.toolbox.seeder.generator.DataGeneratorContext

class SimpleDataPatternGenerator(
    override val identifier: String,
    override val type: R_Type = R_TextType,
    private val generatorFn: (ctx: DataGeneratorContext) -> Any
) : DataPatternGenerator {
    override fun generate(ctx: DataGeneratorContext): Any = generatorFn(ctx)
}