/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.common.toHex
import java.security.MessageDigest

fun sqlConstraintName(entityMountName: String, attrName: String): String {
    val shortName = "$entityMountName:$attrName:size"
    if (shortName.length < 63) {
        return shortName
    }
    val md = MessageDigest.getInstance("SHA-256")
    val ba = md.digest("$entityMountName:$attrName".toByteArray(Charsets.US_ASCII))
    val suffix = ba.toHex().take(8)
    return "${entityMountName.take(22)}:${attrName.take(22)}:$suffix:size"
}
