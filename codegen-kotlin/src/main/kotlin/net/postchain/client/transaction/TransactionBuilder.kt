package net.postchain.client.transaction

import net.postchain.gtv.Gtv

/**
 * TODO: This class is created as a temporary fix as postchain 3.7 has renamed [GTXTransactionBuilder] to TransactionBuilder.
 * When postchain version is increased, remove this class
 */
class TransactionBuilder {
    fun addOperation(name: String, vararg args: Gtv) = Unit
}
