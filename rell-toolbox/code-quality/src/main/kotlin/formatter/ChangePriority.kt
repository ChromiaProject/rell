/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter

internal enum class ChangePriority(val priority: Int) {
    HIGH(1),
    DEFAULT(0),
    LOW(-1),
    SUPER_HIGH(2)
}
