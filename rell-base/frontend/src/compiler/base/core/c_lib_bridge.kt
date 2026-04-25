/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_EntityAttrRef
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_TypeValueMember
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.namespace.C_NamespacePropertyContext
import net.postchain.rell.base.compiler.base.namespace.C_SysNsProto
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.Db_Expr
import net.postchain.rell.base.model.expr.R_DbAtEntity
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.rr.RR_ResolverRuntime
import net.postchain.rell.base.mtype.M_GenericType
import net.postchain.rell.base.utils.ImmList

/**
 * Bridge interface for compiler→lib dependencies. The compiler (frontend) defines this interface;
 * the standard library (runtime) provides the implementation via [C_LibBridge.instance].
 */
interface C_LibBridge {
    fun getSystemLibScope(
        defaultLib: Boolean,
        testLib: Boolean,
        hiddenLib: Boolean,
        extraMod: C_LibModule?,
    ): C_SysLibScope

    fun createSysQueries(executor: C_CompilerExecutor): ImmList<R_QueryDefinition>

    val iterableGenericType: M_GenericType
    val listGenericType: M_GenericType
    val mapGenericType: M_GenericType

    fun getNullExtMembers(name: Name): List<C_TypeValueMember>
    fun transactionRExpr(ctx: C_NamespacePropertyContext, pos: S_Pos): R_Expr

    fun pathToDbExpr(
        ctx: C_ExprContext, atEntity: R_DbAtEntity, path: List<C_EntityAttrRef>, resType: R_Type, pos: S_Pos,
    ): Db_Expr

    val toTextR: Any
    val testOpType: R_Type

    /**
     * Create a fresh, compilation-scoped [RR_ResolverRuntime]. The resolver accumulates
     * sys-fn registrations into this instance; the caller extracts them via the runtime-side
     * API after `resolve()` completes, and pairs them with the produced `RR_App` when building
     * the interpreter. A new instance MUST be returned per call — sharing registrations across
     * compilations breaks isolation of closure captures in stdlib meta-bodies.
     */
    fun newResolverRuntime(): RR_ResolverRuntime

    fun getRellVersionInfo(): Map<*, String>?

    companion object {
        @Volatile
        private var _instance: C_LibBridge? = null

        var instance: C_LibBridge
            get() {
                val existing = _instance
                if (existing != null) return existing
                synchronized(this) {
                    val again = _instance
                    if (again != null) return again
                    // Force-load the implementation class which sets _instance in its <clinit>.
                    Class.forName("net.postchain.rell.base.lib.C_LibBridgeImpl")
                    return checkNotNull(_instance) { "C_LibBridgeImpl did not initialize C_LibBridge.instance" }
                }
            }
            set(value) {
                _instance = value
            }

        fun ensureInitialized() {
            instance // Triggers lazy load.
        }
    }
}

class C_SysLibScope(
    val nsProto: C_SysNsProto,
    val modules: ImmList<C_LibModule>,
)
