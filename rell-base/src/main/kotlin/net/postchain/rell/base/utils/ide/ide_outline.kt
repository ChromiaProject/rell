/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.ide

import net.postchain.rell.base.compiler.ast.S_Name
import net.postchain.rell.base.compiler.ast.S_Node

enum class IdeOutlineNodeType {
    ENTITY,
    OBJECT,
    STRUCT,
    ATTRIBUTE,
    KEY_INDEX,
    ENUM,
    ENUM_ATTRIBUTE,
    NAMESPACE,
    FUNCTION,
    OPERATION,
    QUERY,
    IMPORT,
    CONSTANT,
}

interface IdeOutlineTreeBuilder {
    fun node(node: S_Node, name: S_Name, type: IdeOutlineNodeType): IdeOutlineTreeBuilder
}
