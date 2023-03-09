package net.postchain.rell.codegen.kotlin.util

import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.codegen.section.Entity
import net.postchain.rell.codegen.util.BuiltinType


fun builtin(type: BuiltinType): List<Entity> {
    if (type == BuiltinType.Block) {
        return listOf(BlockEntity)
    }
    if (type == BuiltinType.Transaction) {
        return listOf(TransactionEntity, BlockEntity)
    }
    return emptyList()
}

object BlockEntity : Entity {
    val name = "rell:block"
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("import net.postchain.gtv.mapper.Name", "import ${WrappedByteArray::class.qualifiedName}")

    override fun format() = """
       |class Block(
       |    @Name("block_height") val blockHeight: Long,
       |    @Name("block_rid") val blockRid: WrappedByteArray,
       |    @Name("timestamp") val timestamp: Long,
       |)
    """.trimMargin()
}

object TransactionEntity : Entity {
    val name = "rell:block"
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("import net.postchain.gtv.mapper.Name", "import ${WrappedByteArray::class.qualifiedName}")

    override fun format() = """
       |class Transaction(
       |    @Name("tx_rid") val txRid: WrappedByteArray,
       |    @Name("tx_hash") val txHash: WrappedByteArray,
       |    @Name("tx_data") val txData: WrappedByteArray,
       |    @Name("block") val block: Block,
       |)
    """.trimMargin()
}
