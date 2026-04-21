/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.ImmList

class Db_WhenCase(val conds: ImmList<Db_Expr>, val expr: Db_Expr)

class Db_WhenExpr(
    type: R_Type,
    val keyExpr: Db_Expr?,
    val cases: ImmList<Db_WhenCase>,
    val elseExpr: Db_Expr,
): Db_Expr(type)
