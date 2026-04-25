/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

enum class AtCardinality(val code: String, val zero: Boolean, val many: Boolean) {
    ZERO_ONE("@?", true, false),
    ONE("@", false, false),
    ZERO_MANY("@*", true, true),
    ONE_MANY("@+", false, true),
    ;

    fun matches(count: Int): Boolean = !(count < 0 || count == 0 && !zero || count > 1 && !many)
}
