package net.postchain.gtv

import net.postchain.common.BlockchainRid
import net.postchain.common.types.WrappedByteArray
import java.math.BigInteger

/**
 * TODO: This object is created as a temporary fix as postchain 3.7 has introduced [WrappedByteArray].
 * When postchain version is increased, remove this function
 */
object GtvFactory {

    fun gtv(l: Long): GtvInteger {
        return GtvInteger(l)
    }

    fun gtv(i: BigInteger): GtvBigInteger {
        return GtvBigInteger(i)
    }

    fun gtv(b: Boolean): GtvInteger {
        return GtvInteger(b.toLong())
    }

    fun gtv(s: String): GtvString {
        return GtvString(s)
    }

    fun gtv(ba: ByteArray): GtvByteArray {
        return GtvByteArray(ba)
    }

    fun gtv(wba: WrappedByteArray): GtvByteArray {
        return GtvByteArray(wba.data)
    }

    fun gtv(ba: BlockchainRid): GtvByteArray {
        return GtvByteArray(ba.data)
    }

    fun gtv(vararg a: Gtv): GtvArray {
        return GtvArray(a)
    }

    fun gtv(a: List<Gtv>): GtvArray {
        return GtvArray(a.toTypedArray())
    }

    fun gtv(vararg pairs: Pair<String, Gtv>): GtvDictionary {
        return GtvDictionary.build(mapOf(*pairs))
    }

    fun gtv(dict: Map<String, Gtv>): GtvDictionary {
        return GtvDictionary.build(dict)
    }
}
