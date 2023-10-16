package net.postchain.rell.codegen

interface MermaidCodeGeneratorConfig: CodeGeneratorConfig {
    fun mdx(): Boolean
    fun flowChart(): Boolean
    override fun fileSaveMode() = if (flowChart()) FileSaveMode.Separate else FileSaveMode.Dapp
    override fun allEntities() = !flowChart()
    override fun includeOperations() = flowChart()
    override fun includeQueries() = flowChart()
}
