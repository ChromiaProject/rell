/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern

import io.github.serpro69.kfaker.Faker
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.toolbox.seeder.generator.DataGeneratorContext

interface DataPatternGenerator {
    val identifier: String
    val type: R_Type
        get() = R_TextType

    fun generate(ctx: DataGeneratorContext): Any
}