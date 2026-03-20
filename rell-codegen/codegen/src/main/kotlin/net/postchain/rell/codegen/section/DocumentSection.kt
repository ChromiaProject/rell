package net.postchain.rell.codegen.section

import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.StringSerializable
import net.postchain.rell.codegen.deps.ClassName

interface DocumentSection : StringSerializable {
    val moduleName: String
    val imports: List<String>
    val deps: Set<ClassName>
        get() = setOf()
    val docSymbol: DocSymbol

    /**
     * Only supported in `FileSaveMode.Module`.
     *
     * @return filename without extension and content of file
     */
    fun extraFiles(moduleName: String): List<Pair<String, StringSerializable>> = listOf()
}