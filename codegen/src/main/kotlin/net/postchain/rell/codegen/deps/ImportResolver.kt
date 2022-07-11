package net.postchain.rell.codegen.deps

import mu.KLogging
import net.postchain.rell.codegen.deps.ImportResolver.Companion.extractStructureName
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class ImportResolver {

    companion object : KLogging() {
        fun extractStructureName(
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

        fun appLevelNameToModuleName(str: String): String {
            if (!str.contains(":")) return str.snakeToUpperCamelCase()
            val (module, obj) = str.split(":", limit = 2)
            return "${module.substringBefore("[")}.${obj.snakeToUpperCamelCase()}"

        }
    }

    fun resolveQueryImports(query: R_QueryDefinition): List<String> {
        return resolveQueryDependencies(query).map {appLevelNameToModuleName(it) }
    }

    fun resolveQueryDependencies(query: R_QueryDefinition): Set<String> {
        return resolveQueryOp(query.params(), query.type())
    }
    fun resolveQueryOp(params: List<R_Param>, ret: R_Type?): Set<String> {
        val dependencies = mutableSetOf<String>()
        if (ret is R_StructType) {
            dependencies.add(extractStructureName(ret).first)
            resolveStructureDependencies(ret.struct, dependencies)
        } else if (ret is R_CollectionType && ret.elementType is R_StructType) {
            dependencies.add(extractStructureName(ret.elementType as R_StructType).first)
            resolveStructureDependencies((ret.elementType as R_StructType).struct, dependencies)
        } else if (ret is R_NullableType && ret.valueType is R_StructType) {
            dependencies.add(extractStructureName(ret.valueType as R_StructType).first)
            resolveStructureDependencies((ret.valueType as R_StructType).struct, dependencies)
        } else if (ret is R_NullableType && ret.valueType is R_EnumType) {
            dependencies.add(ret.name.replace("?", ""))
        } else if (ret is R_EnumType) {
            dependencies.add(ret.name)
        }
        params.forEach {
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

    private fun resolveStructureDependencies(struct: R_Struct, dependencies: MutableSet<String>) {
        val r = mutableSetOf<String>()
        struct.attributes.values
            .map { it.type }
            .filterIsInstance<R_CollectionType>()
            .map { it.elementType }
            .filterIsInstance<R_StructType>()
            .forEach {
                dependencies.add(extractStructureName((it)).first)
                resolveStructureDependencies((it).struct, dependencies)
            }
        struct.attributes.values
            .map { it.type }
            .filterIsInstance<R_StructType>()
            .forEach {
                dependencies.add(extractStructureName((it)).first)
                resolveStructureDependencies((it).struct, dependencies)
            }
        dependencies.addAll(struct.attributes.values.filter { it.type is R_EnumType }.map { it.type.name })
    }
}