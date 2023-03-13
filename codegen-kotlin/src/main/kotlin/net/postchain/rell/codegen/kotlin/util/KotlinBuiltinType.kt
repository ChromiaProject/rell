package net.postchain.rell.codegen.kotlin.util

import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.util.BuiltinType


enum class KotlinBuiltinType(override val className: String, override val rellName: String, private val builtin: Builtin) : BuiltinType {
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
        get() = listOf(
                "import net.postchain.gtv.mapper.Name",
                "import ${WrappedByteArray::class.qualifiedName}"
        )

    override fun format() = """
       |class Block(
       |    @Name("block_height") val blockHeight: Long,
       |    @Name("block_rid") val blockRid: WrappedByteArray,
       |    @Name("timestamp") val timestamp: Long,
       |)
    """.trimMargin()
}

object TransactionEntity : Builtin {
    val name = "rell:block"
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf(
                "import net.postchain.gtv.mapper.Name",
                "import ${WrappedByteArray::class.qualifiedName}",
                "import ${RowId::class.qualifiedName}"
        )

    override fun format() = """
       |class Transaction(
       |    @Name("tx_rid") val txRid: WrappedByteArray,
       |    @Name("tx_hash") val txHash: WrappedByteArray,
       |    @Name("tx_data") val txData: WrappedByteArray,
       |    @Name("block") val block: RowId,
       |)
    """.trimMargin()
}
