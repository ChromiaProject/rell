/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.section

object NullSection : DocumentSection, Builtin, Struct, Entity, Enumeration, Operation, Query {
    override val moduleName = ""
    override val imports = listOf<String>()
    override fun format() = ""
}
