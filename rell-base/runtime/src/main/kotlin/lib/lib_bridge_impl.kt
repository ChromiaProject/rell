/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_CompilerExecutor
import net.postchain.rell.base.compiler.base.core.C_LibBridge
import net.postchain.rell.base.compiler.base.core.C_SysLibScope
import net.postchain.rell.base.compiler.base.expr.C_EntityAttrRef
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_TypeValueMember
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.namespace.C_NamespacePropertyContext
import net.postchain.rell.base.lib.test.R_TestOpType
import net.postchain.rell.base.lib.type.Lib_Type_Any
import net.postchain.rell.base.lib.type.Lib_Type_Entity
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.Db_Expr
import net.postchain.rell.base.model.expr.R_DbAtEntity
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.rr.RR_ResolverRuntime
import net.postchain.rell.base.mtype.M_GenericType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.ImmList

internal object C_LibBridgeImpl : C_LibBridge {
    init {
        C_LibBridge.instance = this
        // Register runtime size extractors used by model/r_attr_validator.kt
        R_SizeExtractors.BYTE_ARRAY = { (it as Rt_Value).asByteArray().size }
        R_SizeExtractors.TEXT = { (it as Rt_Value).asString().length }
        // Register SQL context bridge: untyped Any → Rt_SqlContext / Rt_ChainSqlMapping access
        registerSqlBridge()
    }

    private fun registerSqlBridge() {
        R_SqlBridge.mainChainMapping = { sqlCtx -> (sqlCtx as Rt_SqlContext).mainChainMapping() }

        R_SqlBridge.linkedChain = { sqlCtx, chain ->
            val linked = (sqlCtx as Rt_SqlContext).linkedChain(chain)
            R_LinkedChainInfo(linked.sqlMapping, linked.height)
        }

        R_SqlBridge.chainMapping = { sqlCtx, chain -> (sqlCtx as Rt_SqlContext).chainMapping(chain) }
        R_SqlBridge.fullName = { chainMapping, mountName -> (chainMapping as Rt_ChainSqlMapping).fullName(mountName) }
        R_SqlBridge.chainMappingBlocksTable = { chainMapping -> (chainMapping as Rt_ChainSqlMapping).blocksTable }

        R_SqlBridge.chainMappingTransactionsTable = { chainMapping ->
            (chainMapping as Rt_ChainSqlMapping).transactionsTable
        }
    }

    override fun getSystemLibScope(
        defaultLib: Boolean,
        testLib: Boolean,
        hiddenLib: Boolean,
        extraMod: C_LibModule?,
    ): C_SysLibScope = C_SystemLibrary.getScope(C_SystemLibrary.Config(defaultLib, testLib, hiddenLib, extraMod))

    override fun createSysQueries(executor: C_CompilerExecutor): ImmList<R_QueryDefinition> =
        Lib_SysQueries.createQueries(executor)

    override val iterableGenericType: M_GenericType
        get() = Lib_Rell.ITERABLE_TYPE.mGenericType

    override val listGenericType: M_GenericType
        get() = Lib_Rell.LIST_TYPE.mGenericType

    override val mapGenericType: M_GenericType
        get() = Lib_Rell.MAP_TYPE.mGenericType

    override fun getNullExtMembers(name: Name): List<C_TypeValueMember> =
        Lib_Rell.NULL_EXTENSION_TYPE.valueMembers.getByName(name)

    override fun transactionRExpr(ctx: C_NamespacePropertyContext, pos: S_Pos): R_Expr =
        Lib_OpContext.transactionRExpr(ctx, pos)

    override fun pathToDbExpr(
        ctx: C_ExprContext,
        atEntity: R_DbAtEntity,
        path: List<C_EntityAttrRef>,
        resType: R_Type,
        pos: S_Pos,
    ): Db_Expr = Lib_Type_Entity.pathToDbExpr(ctx, atEntity, path, resType, pos)

    override val toTextR: Any
        get() = Lib_Type_Any.ToText_R

    override val testOpType: R_Type
        get() = R_TestOpType

    override fun newResolverRuntime(): RR_ResolverRuntime = Rt_ResolverRuntime()

    override fun getRellVersionInfo(): Map<*, String>? = Rt_RellVersion.getInstance()?.properties
}
