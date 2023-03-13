package net.postchain.rell.codegen.typescript.util

import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.util.BuiltinType

enum class TypescriptBuiltinType(override val className: String, override val rellName: String, private val builtin: Builtin) : BuiltinType {
    Block("Block", BlockEntity.name, BlockEntity),
    Transaction("Transaction", TransactionEntity.name, TransactionEntity)
    ;

    override val module = builtin.moduleName
    override fun createBuiltin(): Builtin {
        return builtin
    }
}

object BlockEntity : Builtin {
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

object TransactionEntity : Builtin {
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
