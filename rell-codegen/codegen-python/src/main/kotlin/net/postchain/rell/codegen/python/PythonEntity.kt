/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Entity

class PythonEntity(className: ClassName, entity: R_EntityDefinition) :
        DataTypeSection(
                className,
                entity.attributes
                        .values
                        .associateBy({ it.name }, { it.type }),
                entity.docSymbol
        ), Entity