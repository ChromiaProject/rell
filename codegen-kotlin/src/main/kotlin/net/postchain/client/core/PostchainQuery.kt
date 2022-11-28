package net.postchain.client.core

import net.postchain.gtv.Gtv

/**
 * TODO: This class is created as a temporary fix as postchain 3.7 has introduced [PostchainQuery] interface.
 * When postchain version is increased, remove this class
 */
fun interface PostchainQuery {
    fun query(name: String, args: Gtv): Gtv
}
