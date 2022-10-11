package net.postchain.client.core

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary

/**
 * TODO: This class is created as a temporary fix as postchain 3.7 has introduced [PostchainQuery] interface.
 * When postchain version is increased, remove this class
 */
interface PostchainQuery {
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv
}
