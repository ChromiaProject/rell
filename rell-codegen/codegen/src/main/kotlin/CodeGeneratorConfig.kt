/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen

interface CodeGeneratorConfig {
    fun allEntities(): Boolean = false
    fun includeQueries(): Boolean = true
    fun includeOperations(): Boolean = true
    fun fileSaveMode(): FileSaveMode = FileSaveMode.Module
}
