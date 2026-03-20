package net.postchain.rell.codegen

interface MermaidCodeGeneratorConfig: CodeGeneratorConfig {
    fun mdx(): Boolean
    fun erDiagram(): Boolean
    override fun fileSaveMode() =  FileSaveMode.Dapp
    override fun allEntities() = true
    override fun includeOperations() = false
    override fun includeQueries() = false
}
