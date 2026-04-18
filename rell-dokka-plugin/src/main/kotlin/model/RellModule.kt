/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.model

import net.postchain.rell.base.model.*

data class RellModule(
        val operations: Collection<R_OperationDefinition>,
        val queries: Collection<R_QueryDefinition>,
        val functions: Collection<R_FunctionDefinition>,
        val constants: Collection<R_GlobalConstantDefinition>,
        val entities: Collection<R_EntityDefinition>,
        val structs: Collection<R_StructDefinition>,
        val enums: Collection<R_EnumDefinition>,
        val objects: Collection<R_ObjectDefinition>,
)
