/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.typescript.util

import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.util.BuiltinType

enum class TypescriptBuiltinType(val className: String, private val builtin: Builtin) : BuiltinType {
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
       |export type Block = {
       |    block_height: number,
       |    block_rid: Buffer,
       |    timestamp: number,
       |}
    """.trimMargin()
}

object TransactionEntity : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
       |export type Transaction = {
       |    tx_rid: Buffer,
       |    tx_hash: Buffer,
       |    tx_data: Buffer,
       |    block: number,
       |}
    """.trimMargin()
}


object GtxOperationStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
       |export type GtxOperation = {
       |    name: string,
       |    args: any[],
       |}
    """.trimMargin()
}


object GtxTransactionBodyStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
       |export type GtxTransactionBody = {
       |    blockchain_rid: Buffer,
       |    operations: GtxOperation[],
       |    signers: any[],
       |}
    """.trimMargin()
}


object GtxTransactionStruct : Builtin {
    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
       |export type GtxTransaction = {
       |    body: GtxTransactionBody,
       |    signatures: any[],
       |}
    """.trimMargin()
}
