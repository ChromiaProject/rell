package com.chromia.rell.dokka.model

import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_Module
import org.jetbrains.dokka.links.DRI

// Module == kotlin package
fun R_App.definitionsByModule(): Map<R_Module, RellModule> {
    return modules.associateWith { m ->
        RellModule(
                m.operations.values,
                m.queries.values,
                m.functions.values,
                m.constants.values,
                m.entities.values,
                m.structs.values,
                m.enums.values,
                m.objects.values
        )
    }
}

fun R_Module.toDRI() = DRI(name.str())