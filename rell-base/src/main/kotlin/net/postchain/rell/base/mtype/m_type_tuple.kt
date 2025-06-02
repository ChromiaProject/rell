/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList

object M_TupleTypeUtils {
    private val NULL_NAMES: ImmList<ImmList<String?>> = (0 .. 50).mapToImmList { n ->
        ImmList(n) { null }
    }

    private fun getNullNames(n: Int): ImmList<String?> {
        check(n > 0) { n }
        return if (n < NULL_NAMES.size) NULL_NAMES[n] else (0 until n).mapToImmList { null }
    }

    fun <T> makeNames(fields: List<T>, nameGetter: (T) -> String?): ImmList<String?> {
        return if (fields.all { nameGetter(it) == null }) {
            getNullNames(fields.size)
        } else {
            fields.mapToImmList { nameGetter(it) }
        }
    }

    fun makeType(types: List<M_Type>): M_Type {
        val names = getNullNames(types.size)
        return makeType(types, names)
    }

    fun makeType(types: List<M_Type>, names: List<String?>): M_Type {
        return M_Type_Tuple_Internal(types.toImmList(), names.toImmList())
    }
}

sealed class M_Type_Tuple(
    val fieldTypes: ImmList<M_Type>,
    val fieldNames: ImmList<String?>,
): M_Type_Composite(fieldTypes.size) {
    init {
        check(this.fieldTypes.isNotEmpty())
        checkEquals(this.fieldNames.size, this.fieldTypes.size)
    }
}

private class M_Type_Tuple_Internal(
    fieldTypes: ImmList<M_Type>,
    fieldNames: ImmList<String?>,
): M_Type_Tuple(fieldTypes, fieldNames) {
    override val canonicalArgs: ImmList<M_TypeSet> = fieldTypes.mapToImmList { M_TypeSets.one(it) }

    override fun strCode(): String {
        return fieldNames.indices.joinToString(",", "(", ")") { i ->
            val name = fieldNames[i]
            val typeStr = fieldTypes[i].strCode()
            if (name == null) typeStr else "$name:$typeStr"
        }
    }

    override fun equalsComposite0(other: M_Type_Composite): Boolean =
        other is M_Type_Tuple_Internal && fieldNames == other.fieldNames

    override fun hashCodeComposite0() = fieldNames.hashCode()

    override fun getTypeArgVariance(index: Int) = M_TypeVariance.OUT

    override fun captureWildcards(): M_Type = this

    override fun newInstance(newArgs: List<M_TypeSet>): M_Type_Composite {
        checkEquals(newArgs.size, fieldNames.size)
        val newFieldTypes = newArgs.mapToImmList { it.canonicalOutType() }
        return M_Type_Tuple_Internal(newFieldTypes, fieldNames)
    }

    override fun getCorrespondingSuperType(otherType: M_Type_Composite): M_Type_Composite? {
        return if (otherType is M_Type_Tuple_Internal && otherType.fieldNames == fieldNames) this else null
    }

    override fun validate() {
        for (fieldType in fieldTypes) {
            fieldType.validate()
        }
    }
}
