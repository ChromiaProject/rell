package net.postchain.rell.codegen.typescript.util

import net.postchain.rell.codegen.section.Entity
import net.postchain.rell.codegen.util.BuiltinType


fun builtin(type: BuiltinType): Entity {
    return when (type) {
        BuiltinType.Block -> BlockEntity
        BuiltinType.Transaction -> TransactionEntity
    }
}

object BlockEntity : Entity {
    val name = "rell:block"
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
       |export type Block = {
       |    blockHeight: number,
       |    blockRid: Buffer,
       |    timestamp: number,
       |}
    """.trimMargin()
}

object TransactionEntity : Entity {
    val name = "rell:block"
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
       |export type Transaction = {
       |    txRid: Buffer,
       |    txHash: Buffer,
       |    txData: Buffer,
       |    block: number,
       |}
    """.trimMargin()
}
