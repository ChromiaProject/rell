/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

enum class C_MessageType(val text: String, val ignorable: Boolean) {
    WARNING("Warning", true),
    ERROR("ERROR", false)
}
