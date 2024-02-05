/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

class DocException(val code: String, msg: String): RuntimeException(msg) {
    companion object {
        fun check(b: Boolean, codeMsgLazy: () -> Pair<String, String>) {
            if (!b) {
                val pair = codeMsgLazy()
                throw DocException(pair.first, pair.second)
            }
        }
    }
}
