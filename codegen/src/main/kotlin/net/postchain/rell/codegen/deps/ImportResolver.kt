package net.postchain.rell.codegen.deps

import mu.KLogging
import net.postchain.rell.model.*

class ImportResolver {

    companion object : KLogging()

    fun resolveQueryDependencies(query: R_QueryDefinition): Set<String> {
        val dependencies = mutableSetOf<String>()
        val ret = query.type()
        if (ret is R_StructType) {
            dependencies.add(extractStructureName(ret).first)
            resolveStructureDependencies(ret.struct, dependencies)
        } else if (ret is R_CollectionType && ret.elementType is R_StructType) {
            dependencies.add(extractStructureName(ret.elementType as R_StructType).first)
            resolveStructureDependencies((ret.elementType as R_StructType).struct, dependencies)
        } else if (ret is R_EnumType) {
            dependencies.add(ret.name)
        }
        query.params().forEach {
            val t = it.type
            if (t is R_StructType) {
                dependencies.add(extractStructureName(t).first)
                resolveStructureDependencies(t.struct, dependencies)
            } else if (t is R_EnumType) {
                dependencies.add(t.name)
            }
        }
        return dependencies
    }

    private fun extractStructureName(
        struct: R_StructType,
    ): Pair<String, R_StructType> {
        return if (struct.name.contains("struct<")) { // Ad-hoc structs
            // entities foo:bar and external entities foo[foo]:bar
            if (struct.name.contains(":")) { // Entities
                val element = struct.name.substringAfter("<").replace(">", "")
                element to struct
            } else {
                // Custom struct
                throw IllegalArgumentException("Could not resolve name")
            }
        } else {
            struct.name to struct
        }
    }

    private fun resolveStructureDependencies(struct: R_Struct, dependencies: MutableSet<String>) {
        val r = mutableSetOf<String>()
        struct.attributes.values
            .filter { it.type is R_StructType }
            .map { it.type as R_StructType }
            .forEach {
                dependencies.add(extractStructureName((it)).first)
                resolveStructureDependencies((it).struct, dependencies)
            }
        dependencies.addAll(struct.attributes.values.filter { it.type is R_EnumType }.map { it.type.name })
    }
}