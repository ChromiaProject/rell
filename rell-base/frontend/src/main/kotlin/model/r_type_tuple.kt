/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.mtype.M_TupleTypeUtils
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocType

class R_TupleType(val fields: ImmList<R_TupleField>): R_CompositeType(calcName(fields)) {
    val virtualType = R_VirtualTupleType(this)

    init {
        check(this.fields.isNotEmpty())
        for (i in this.fields.indices) {
            checkEquals(this.fields[i].index, i)
        }
    }

    private val isError = fields.any { it.type.isError() }

    override fun equals0(other: R_Type): Boolean = other is R_TupleType && fields == other.fields
    override fun hashCode0() = fields.hashCode()

    override fun isComparable() = fields.all { it.type.isComparable() }
    override fun isDirectPure() = true
    override fun isReference() = true

    override fun isError() = isError
    override fun isDirectMutable() = false
    override fun isDirectMixedTuple() = fields.any { it.name != null } && fields.any { it.name == null }
    override fun isValid() = fields.all { it.type.isValid() }

    override fun strCode() = name

    override fun getLibType0(): C_LibType {
        val fieldTypes = fields.map { it.type.mType }
        val fieldNames = M_TupleTypeUtils.makeNames(fields) { it.name?.str }
        val mType = M_Types.tuple(fieldTypes, fieldNames)
        return C_LibType.make(
            mType,
            valueMembers = R_LibTypeMemberRegistry.getValueMembers(this),
        )
    }

    override fun getTypeMeta0(): R_TypeMeta = Meta()
    override fun getTypeArgs() = fields.mapToImmList { it.type }

    override fun isAssignableFrom(type: R_Type): Boolean {
        if (type !is R_TupleType) return false
        if (fields.size != type.fields.size) return false

        for (i in fields.indices) {
            val field = fields[i]
            val otherField = type.fields[i]
            if (field.name != otherField.name) return false
            if (!field.type.isAssignableFrom(otherField.type)) return false
        }

        return true
    }

    override fun calcCommonType(other: R_Type): R_Type? {
        if (other !is R_TupleType) return null
        if (fields.size != other.fields.size) return null

        val resFields = fields.mapIndexedToImmList { i, field ->
            val otherField = other.fields[i]
            if (field.name != otherField.name) return@calcCommonType null

            val type = commonTypeOpt(field.type, otherField.type) ?: return@calcCommonType null

            when (type) {
                field.type -> field
                otherField.type -> otherField
                else -> R_TupleField(i, field.name, type)
            }
        }

        return R_TupleType(resFields)
    }

    override fun calcParentType(): R_Type? {
        return when {
            fields.size == 2 -> R_GenericType("map_entry", immListOf(fields[0].type, fields[1].type))
            else -> super.calcParentType()
        }
    }

    override fun calcTypeExtractors(): ImmMap<String, (R_Type) -> R_Type?> {
        return fields.flatMapIndexed { i, field ->
                field.type.typeExtractors.map {
                    it.key to { srcType: R_Type ->
                        if (srcType is R_TupleType && srcType.fields.size == fields.size) {
                            it.value(srcType.fields[i].type)
                        } else null
                    }
                }
            }
            .toImmMap()
    }

    override fun docType(): DocType {
        val fieldTypes = fields.mapToImmList { it.type.docType() }
        return DocType.tuple(fieldTypes, fields.mapToImmList { it.name?.str })
    }

    private inner class Meta: R_TypeMeta() {
        override fun getTypeOrNull(args: ImmList<R_Type>): R_Type {
            checkEquals(args.size, fields.size)
            val resFields = fields.mapIndexedToImmList { i, f -> R_TupleField(f.index, f.name, args[i]) }
            return R_TupleType(resFields)
        }
    }

    companion object {
        private fun calcName(fields: List<R_TupleField>): String {
            val fieldsStr = fields.joinToString(",") { it.strCode() }
            val comma = if (fields.size == 1 && fields[0].name == null) "," else ""
            return "($fieldsStr$comma)"
        }

        fun make(vararg fields: R_Type): R_TupleType {
            return make(fields.toList())
        }

        fun make(fields: List<R_Type>): R_TupleType {
            val fieldsList = fields.mapIndexedToImmList { i, type -> R_TupleField(i, null, type) }
            return R_TupleType(fieldsList)
        }

        fun makeNamed(vararg fields: Pair<String?, R_Type>): R_TupleType {
            val fieldsList = fields.mapIndexedToImmList { i, (name, type) ->
                val rIdeName = name?.let { s -> R_IdeName(Name.of(s), C_IdeSymbolInfo.MEM_TUPLE_ATTR) }
                R_TupleField(i, rIdeName, type)
            }
            return R_TupleType(fieldsList)
        }
    }
}

class R_TupleField(val index: Int, val name: R_IdeName?, val type: R_Type) {
    init {
        check(index >= 0)
    }

    fun str(): String = strCode()

    fun strCode(): String {
        return if (name != null) "${name}:${type.strCode()}" else type.strCode()
    }

    override fun toString(): String {
        CommonUtils.failIfUnitTest()
        return str()
    }

    override fun equals(other: Any?): Boolean = other === this || (other is R_TupleField && name == other.name && type == other.type)
    override fun hashCode() = java.util.Objects.hash(name, type)

}
