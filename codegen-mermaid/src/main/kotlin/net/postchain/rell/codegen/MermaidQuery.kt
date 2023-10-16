package net.postchain.rell.codegen

import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.codegen.section.Query

class MermaidQuery(private val queryDef: R_QueryDefinition): Query {
    override fun format(): String {
        return ""//queryDef.call()
    }

    override val moduleName: String
        get() = TODO("Not yet implemented")
    override val imports: List<String>
        get() = TODO("Not yet implemented")
}