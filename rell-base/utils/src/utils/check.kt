/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class) fun <T> checkNull(actual: T) {
    contract {
        returns() implies (actual == null)
    }
    if (actual != null) {
        error("Requires value was not null.")
    }
}

@OptIn(ExperimentalContracts::class) inline fun <T> checkNull(actual: T, lazyMessage: () -> Any) {
    contract {
        returns() implies (actual == null)
        callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
    }
    if (actual != null) {
        error(lazyMessage())
    }
}

fun <T> checkEquals(actual: T, expected: T) = check(actual == expected) { "Expected <$expected>, actual <$actual>." }

@OptIn(ExperimentalContracts::class)
inline fun <T> checkEquals(actual: T, expected: T, lazyMessage: () -> Any) {
    contract {
        callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
    }
    if (actual != expected) {
        error(lazyMessage())
    }
}
