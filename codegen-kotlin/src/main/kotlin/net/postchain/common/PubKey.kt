package net.postchain.crypto

import net.postchain.common.types.WrappedByteArray

data class PubKey(private val wData: WrappedByteArray) { val data = wData.data }
