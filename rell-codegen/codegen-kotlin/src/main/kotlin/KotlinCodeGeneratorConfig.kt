/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.CodeGeneratorConfig

 interface KotlinCodeGeneratorConfig: CodeGeneratorConfig {
    fun packageName(): String
}
