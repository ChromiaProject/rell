package net.postchain.rell.codegen.deps

import mu.KLogging
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class ImportResolver {

    companion object : KLogging() {
        fun extractStructureName( struct: R_StructType, ): String {
            return CamelCaseClassName.fromString(struct.name).rellName
        }

        fun appLevelNameToModuleName(str: String): String {
            return CamelCaseClassName.fromString(str).toPackageName()
        }
    }

    fun resolveOperationDependencies(op: R_OperationDefinition): Set<String> {
        return resolveQueryOp(op.params(), null)
    }

    fun resolveQueryDependencies(query: R_QueryDefinition): Set<String> {
        return resolveQueryOp(query.params(), query.type())
    }
    fun resolveQueryOp(params: List<R_Param>, ret: R_Type?): Set<String> {
        val dependencies = mutableSetOf<String>()
        if (ret is R_StructType) {
            dependencies.add(extractStructureName(ret))
            resolveStructureDependencies(ret.struct, dependencies)
        } else if (ret is R_TupleType) {
            val tupleTypes = ret.fields.map { it.type }
            tupleTypes.filterIsInstance<R_StructType>()
                .forEach {
                    dependencies.add(extractStructureName(it))
                    resolveStructureDependencies((it).struct, dependencies)
                }
            tupleTypes.filterIsInstance<R_EnumType>()
                .forEach { dependencies.add(it.name) }
        } else if (ret is R_CollectionType && ret.elementType is R_StructType) {
            dependencies.add(extractStructureName(ret.elementType as R_StructType))
            resolveStructureDependencies((ret.elementType as R_StructType).struct, dependencies)
        } else if (ret is R_NullableType && ret.valueType is R_StructType) {
            dependencies.add(extractStructureName(ret.valueType as R_StructType))
            resolveStructureDependencies((ret.valueType as R_StructType).struct, dependencies)
        } else if (ret is R_NullableType && ret.valueType is R_EnumType) {
            dependencies.add(ret.name.replace("?", ""))
        } else if (ret is R_EnumType) {
            dependencies.add(ret.name)
        }
        params.forEach {
            val t = it.type
            if (t is R_StructType) {
                dependencies.add(extractStructureName(t))
                resolveStructureDependencies(t.struct, dependencies)
            } else if (t is R_EnumType) {
                dependencies.add(t.name)
            }
        }
        return dependencies
    }

    private fun resolveStructureDependencies(struct: R_Struct, dependencies: MutableSet<String>) {
        struct.attributes.values
            .map { it.type }
            .filterIsInstance<R_CollectionType>()
            .map { it.elementType }
            .filterIsInstance<R_StructType>()
            .forEach {
                dependencies.add(extractStructureName((it)))
                resolveStructureDependencies((it).struct, dependencies)
            }
        struct.attributes.values
            .map { it.type }
            .filterIsInstance<R_StructType>()
            .forEach {
                dependencies.add(extractStructureName((it)))
                resolveStructureDependencies((it).struct, dependencies)
            }
        dependencies.addAll(struct.attributes.values.filter { it.type is R_EnumType }.map { it.type.name })
    }
}