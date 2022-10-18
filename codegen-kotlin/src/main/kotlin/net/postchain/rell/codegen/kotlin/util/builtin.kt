package net.postchain.rell.codegen.kotlin.util

import net.postchain.rell.codegen.section.Entity

class BlockEntity() : Entity {
    val name = "rell:block"
    override val moduleName = "rell"
    override val imports: List<String>
        get() = listOf("import net.postchain.gtv.mapper.Name")

    override fun format() = """
       |class Block(
       |    @Name("block_height") val blockHeight: Long,
       |    @Name("block_rid") val blockRid: WrappedByteArray,
       |    @Name("timestamp") val timestamp: Long,
       |)
    """.trimMargin()
}

class TransactionEntity() : Entity {
    val name = "rell:block"
    override val moduleName = "rell"
    override val imports: List<String>
        get() = listOf("import net.postchain.gtv.mapper.Name")

    override fun format() = """
       |class Transaction(
       |    @Name("tx_rid") val txRid: WrappedByteArray,
       |    @Name("tx_hash") val txHash: WrappedByteArray,
       |    @Name("tx_data") val txData: WrappedByteArray,
       |    @Name("block") val block: Block,
       |)
    """.trimMargin()
}
