package net.postchain.rell.codegen

interface CodeGeneratorConfig {
    fun allEntities(): Boolean = false
    fun includeQueries(): Boolean = true
    fun includeOperations(): Boolean = true
    fun fileSaveMode(): CodeGenerator.FileSaveMode = CodeGenerator.FileSaveMode.Module
}
