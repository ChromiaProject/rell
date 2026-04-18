/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter

enum class BracePairTypes(var opening: String, var closing: String) {
    CURLY("{", "}"),
    BRACKETS("[", "]"),
    PARENTHESES("(", ")"),
    ANGLE("<", ">")
}
