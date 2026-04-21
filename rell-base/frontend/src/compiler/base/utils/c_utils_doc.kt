/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueEvalContext
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.toImmMap

typealias DocSymbolTransformer = (DocSymbol) -> DocSymbol

object C_DocUtils {
    /** Returns `null` if the value cannot be converted to the doc format (either it's too big, or the type is not
     * supported). */
    fun docValue(value: RR_ConstantValue): DocValue? {
        return when (value) {
            is RR_ConstantValue.Null -> DocValue.NULL
            is RR_ConstantValue.Unit -> DocValue.UNIT
            is RR_ConstantValue.Bool -> DocValue.boolean(value.value)
            is RR_ConstantValue.Int -> DocValue.integer(value.value)
            is RR_ConstantValue.BigInteger -> DocValue.bigInteger(java.math.BigInteger(value.value))
            is RR_ConstantValue.Decimal -> DocValue.decimal(java.math.BigDecimal(value.value))
            is RR_ConstantValue.Text -> DocValue.text(value.value)
            is RR_ConstantValue.ByteArray -> DocValue.byteArray(value.value)
            is RR_ConstantValue.Rowid -> DocValue.rowid(value.value)
            else -> null
        }
    }

    fun docExpr(vExpr: V_Expr): DocExpr {
        val rrValue = try {
            vExpr.constantValue(V_ConstantValueEvalContext())
        } catch (e: Throwable) {
            null
        }
        val docValue = if (rrValue == null) null else docValue(rrValue)
        return if (docValue == null) DocExpr.UNKNOWN else DocExpr.value(docValue)
    }

    fun docModifiers(deprecated: C_Deprecated? = null): DocModifiers {
        return DocModifiers.make(docModifier(deprecated))
    }

    fun docModifier(deprecated: C_Deprecated?): DocModifier? {
        return if (deprecated == null) null else DocModifier.deprecated(deprecated.error)
    }

    fun getAllComments(rApp: R_App): ImmMap<String, String> {
        val map = mutableMapOf<String, String>()
        val set = mutableSetOf<DocDefinition>()
        for (module in rApp.modules.sortedBy { it.name }) {
            getAllComments0(module, "${module.name.str()}:", map, set)
        }
        return map.toImmMap()
    }

    private fun getAllComments0(
        def: DocDefinition,
        baseName: String,
        map: MutableMap<String, String>,
        set: MutableSet<DocDefinition>,
    ) {
        if (!set.add(def)) {
            return
        }

        for ((name, subDef) in def.docMembers) {
            val subName = "${baseName}$name"
            val comment = subDef.docSymbol.comment
            if (comment != null) {
                map[subName] = comment.strCode()
            }
            getAllComments0(subDef, "$subName.", map, set)
        }
    }
}
