package net.postchain.common

import net.postchain.common.types.WrappedByteArray

/**
 * TODO: This function is created as a temporary fix as postchain 3.7 has introduced [WrappedByteArray].
 * When postchain version is increased, remove this function
 */
fun ByteArray.wrap() = WrappedByteArray(this)
