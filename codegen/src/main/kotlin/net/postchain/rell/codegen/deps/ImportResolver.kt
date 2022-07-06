package net.postchain.rell.codegen.deps

import net.postchain.rell.model.R_EnumType
import net.postchain.rell.model.R_QueryDefinition
import net.postchain.rell.model.R_Struct
import net.postchain.rell.model.R_StructType

class ImportResolver {

    fun resolveQueryImports(query: R_QueryDefinition): Set<String> {
        val dependencies = mutableSetOf<String>()
        val ret = query.type()
        if (ret is R_StructType) {
            resolveImports(ret, dependencies)
        } else if (ret is R_EnumType) {
            dependencies.add(ret.name)
        }
        query.params().forEach {
            val t = it.type
            if (t is R_StructType) {
                resolveImports(t, dependencies)
            } else if (t is R_EnumType) {
                dependencies.add(t.name)
            }
        }
        return dependencies
    }

    private fun resolveImports(
        struct: R_StructType,
        dependencies: MutableSet<String>
    ) {
        if (struct.name.contains("struct<")) { // Ad-hoc structs
            if (struct.name.contains(":")) { // Entities
                dependencies.add(struct.name.substringAfter("<").replace(">", ""))
            } else {
                // Custom struct
            }
        } else {
            dependencies.add(struct.name)
            dependencies.addAll(resolveImports(struct.struct))
        }
    }

    private fun resolveImports(struct: R_Struct): Set<String> {
        val r = mutableSetOf<String>()
        val s = struct.attributes.filterValues { it.type is R_StructType }.values
        r.addAll(s.map { it.type.name })
        s.forEach {
            val t = it.type
            if (t is R_StructType) {
                r.addAll(resolveImports(t.struct))
            }
        }
        r.addAll(struct.attributes.values.filter{ it.type is R_EnumType }.map { it.type.name })
        return r
    }
}