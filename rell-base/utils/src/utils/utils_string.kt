/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.common.hexStringToByteArray
import java.util.*

fun String.hexStringToBytes(): Bytes = this.hexStringToByteArray().toBytes()

fun String.formatEx(vararg args: Any?): String = format(Locale.ROOT, *args)
