package net.postchain.rell.codegen.deps

import mu.KLogging
import net.postchain.rell.model.R_EnumType
import net.postchain.rell.model.R_QueryDefinition
import net.postchain.rell.model.R_Struct
import net.postchain.rell.model.R_StructType

class ImportResolver {

    companion object : KLogging()

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
                val element = struct.name.substringAfter("<").replace(">", "")
                dependencies.add(element.substringBefore("["))
            } else {
                // Custom struct
                dependencies
            }
        } else {
            dependencies.add(struct.name)
            dependencies.addAll(resolveImports(struct.struct, dependencies))
        }
    }

    private fun resolveImports(struct: R_Struct, dependencies: MutableSet<String>): Set<String> {
        val r = mutableSetOf<String>()
        val s = struct.attributes.filterValues { it.type is R_StructType }.values
        s.forEach { resolveImports((it.type as R_StructType), dependencies) }
        s.forEach {
            val t = it.type
            if (t is R_StructType) {
                r.addAll(resolveImports(t.struct, dependencies))
            }
        }
        r.addAll(struct.attributes.values.filter{ it.type is R_EnumType }.map { it.type.name })
        return r
    }
}