/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Struct

class PythonStruct(className: ClassName, structDef: R_StructDefinition) :
        DataTypeSection(
                className,
                structDef.struct
                        .attributes
                        .values
                        .associateBy({ it.name }, { it.type }),
                structDef.docSymbol
        ), Struct