package net.postchain.rell.codegen.kotlin.util

import net.postchain.rell.codegen.section.Entity

class BlockEntity() : Entity {
    override val name = "Block"
    override val imports: List<String>
        get() = listOf("import net.postchain.gtv.mapper.Name")

    override fun format() = """
       |class Block(
       |    @Name("block_height") val blockHeight: Long,
       |    @Name("block_rid") val blockRid: ByteArray,
       |    @Name("timestamp") val timestamp: Long,
       |)
    """.trimMargin()
}

class TransactionEntity() : Entity {
    override val name = "Transaction"
    override val imports: List<String>
        get() = listOf("import net.postchain.gtv.mapper.Name")

    override fun format() = """
       |class Transaction(
       |    @Name("tx_rid") val txRid: ByteArray,
       |    @Name("tx_hash") val txHash: ByteArray,
       |    @Name("tx_data") val txData: ByteArray,
       |    @Name("block") val block: Block,
       |)
    """.trimMargin()
}
