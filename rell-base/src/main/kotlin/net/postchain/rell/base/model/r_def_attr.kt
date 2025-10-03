/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.MsgString
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSourcePos
import net.postchain.rell.base.utils.doc.DocSymbol

class R_Attribute(
    val index: Int,
    val rName: R_Name,
    val type: R_Type,
    val mutable: Boolean,
    val keyIndexKind: R_KeyIndexKind?,
    val ideInfo: C_IdeSymbolInfo,
    val restrictions: C_MemberRestrictions = C_MemberRestrictions.NULL,
    val canSetInCreate: Boolean = true,
    val sqlMapping: String = rName.str,
    internal val validator: R_AttrValidator? = null,
    override val docSourcePos: DocSourcePos? = null,
    private val exprGetter: C_LateGetter<R_DefaultValue>?,
): DocDefinition() {
    val ideName = R_IdeName(rName, ideInfo)
    val name = rName.str

    val expr: R_Expr? get() = exprGetter?.get()?.rExpr
    val isExprDbModification: Boolean get() = exprGetter?.get()?.isDbModification ?: false

    val hasExpr: Boolean get() = exprGetter != null

    override val docSymbol: DocSymbol get() = ideInfo.getIdeInfo().doc ?: DocSymbol.NONE

    fun toMetaGtv(): Gtv {
        return mapOf(
            "name" to name.toGtv(),
            "type" to type.toMetaGtv(),
            "mutable" to mutable.toGtv(),
        ).toGtv()
    }

    fun copy(mutable: Boolean, ideInfo: C_IdeSymbolInfo): R_Attribute {
        return R_Attribute(
            index = index,
            rName = rName,
            type = type,
            mutable = mutable,
            keyIndexKind = keyIndexKind,
            ideInfo = ideInfo,
            docSourcePos = docSourcePos,
            restrictions = restrictions,
            canSetInCreate = true,
            sqlMapping = sqlMapping,
            validator = validator,
            exprGetter = if (canSetInCreate) exprGetter else null, // Not copying default value e. g. for "transaction".
        )
    }

    override fun toString() = name
}

class R_DefaultValue(val rExpr: R_Expr, val isDbModification: Boolean)

enum class R_KeyIndexKind(val code: String) {
    KEY("key"),
    INDEX("index"),
    ;

    val nameMsg = MsgString(code)
}
