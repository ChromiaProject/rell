package net.postchain.rell.codegen.kotlin.util

import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.util.BuiltinType


enum class KotlinBuiltinType(val className: String, private val builtin: Builtin) : BuiltinType {
    Block("Block", BlockEntity),
    Transaction("Transaction", TransactionEntity),
    GtxOperation("GtxOperation", GtxOperationStruct),
    GtxTransactionBody("GtxTransactionBody", GtxTransactionBodyStruct),
    GtxTransaction("GtxTransaction", GtxTransactionStruct)
    ;

    override fun createBuiltin(): Builtin {
        return builtin
    }
}

object BlockEntity : Builtin {
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

object GtxOperationStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf(
                "import net.postchain.gtv.mapper.Name",
                "import net.postchain.gtv.Gtv",
        )

    override fun format() = """
       |class GtxOperation(
       |    @Name("name") val name: String,
       |    @Name("args") val args: List<Gtv>,
       |)
    """.trimMargin()
}

object GtxTransactionBodyStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf(
                "import net.postchain.gtv.mapper.Name",
                "import net.postchain.gtv.Gtv",
                "import ${WrappedByteArray::class.qualifiedName}",
        )

    override fun format() = """
       |class GtxTransactionBody(
       |    @Name("blockchain_rid") val blockchainRid: WrappedByteArray,
       |    @Name("operations") val operations: List<GtxOperation>,
       |    @Name("signers") val signers: List<Gtv>,
       |)
    """.trimMargin()
}

object GtxTransactionStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf(
                "import net.postchain.gtv.mapper.Name",
                "import net.postchain.gtv.Gtv",
        )

    override fun format() = """
       |class GtxTransaction(
       |    @Name("body") val body: GtxTransactionBody,
       |    @Name("signatures") val signatures: List<Gtv>,
       |)
    """.trimMargin()
}
