package net.postchain.rell.codegen.python.util

import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.util.BuiltinType

enum class PythonBuiltinType(val className: String, private val builtin: Builtin) : BuiltinType {
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
        get() = listOf("")

    override fun format() = """
        |@dataclass
        |class Block:
        |    block_height: int
        |    block_rid: bytes
        |    timestamp: int
    """.trimMargin()
}

object TransactionEntity : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
        |@dataclass
        |class Transaction:
        |   tx_rid: bytes
        |   tx_hash: bytes
        |   tx_data: bytes
        |   block: int
    """.trimMargin()
}

object GtxOperationStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
        |@dataclass
        |class GtxOperation:
        |    name: str
        |    args: List[Any]
    """.trimMargin()
}

object GtxTransactionBodyStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
        |@dataclass
        |class GtxTransactionBody:
        |    blockchain_rid: bytes
        |    operations: List['GtxOperation']
        |    signers: List[Any]
    """.trimMargin()
}

object GtxTransactionStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
        |@dataclass
        |class GtxTransaction:
        |    body: 'GtxTransactionBody' 
        |    signatures: List[Any]
    """.trimMargin()
} 