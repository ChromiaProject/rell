package net.postchain.rell.codegen.deps

import mu.KLogging
import net.postchain.rell.model.*

class ImportResolver {

    companion object : KLogging() {
        fun extractStructureName(struct: R_StructType): ClassName {
            return CamelCaseClassName.fromString(struct.name)
        }
    }

    fun resolveOperationDependencies(op: R_OperationDefinition): Set<ClassName> {
        return resolveQueryOp(op.params(), null)
    }

    fun resolveQueryDependencies(query: R_QueryDefinition): Set<ClassName> {
        return resolveQueryOp(query.params(), query.type())
    }

    fun resolveQueryOp(params: List<R_Param>, ret: R_Type?): Set<ClassName> {
        val dependencies = mutableSetOf<ClassName>()
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
                .forEach { dependencies.add(CamelCaseClassName.fromString(it.name)) }
        } else if (ret is R_CollectionType && ret.elementType is R_StructType) {
            dependencies.add(extractStructureName(ret.elementType as R_StructType))
            resolveStructureDependencies((ret.elementType as R_StructType).struct, dependencies)
        } else if (ret is R_NullableType && ret.valueType is R_StructType) {
            dependencies.add(extractStructureName(ret.valueType as R_StructType))
            resolveStructureDependencies((ret.valueType as R_StructType).struct, dependencies)
        } else if (ret is R_NullableType && ret.valueType is R_EnumType) {
            dependencies.add(CamelCaseClassName.fromString(ret.name))
        } else if (ret is R_EnumType) {
            dependencies.add(CamelCaseClassName.fromString(ret.name))
        }
        params.forEach {
            val t = it.type
            if (t is R_StructType) {
                dependencies.add(extractStructureName(t))
                resolveStructureDependencies(t.struct, dependencies)
            } else if (t is R_EnumType) {
                dependencies.add(CamelCaseClassName.fromString(t.name))
            }
        }
        return dependencies
    }

    private fun resolveStructureDependencies(struct: R_Struct, dependencies: MutableSet<ClassName>) {
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
        dependencies.addAll(struct.attributes.values.filter { it.type is R_EnumType }.map { CamelCaseClassName.fromString(it.type.name) })
    }
}