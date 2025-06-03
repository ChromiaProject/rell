/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.core.C_QualifiedName
import net.postchain.rell.base.compiler.base.core.C_VarId
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty
import net.postchain.rell.base.compiler.base.namespace.C_NamespacePropertyContext
import net.postchain.rell.base.compiler.base.utils.C_Constants
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.type.Rt_ByteArrayValue
import net.postchain.rell.base.lib.type.Rt_GtvValue
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_SysFunctionEx_N
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.checkEquals

object Lib_ChainContext {
    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("chain_context", since = "0.7.0") {
            comment("""
                Access information and configuration relating to the blockchain of this Rell DApp.
            """)
            property("raw_config", type = "gtv", pure = false, since = "0.7.0") {
                comment("""
                    The configuration object for the blockchain of this Rell DApp.
                """)
                value { ctx ->
                    Rt_GtvValue.get(ctx.chainCtx.rawConfig)
                }
            }

            property("blockchain_rid", type = "byte_array", pure = false, since = "0.9.0") {
                comment("""
                    The RID of the blockchain of this Rell DApp.

                    A byte array of size `32`.
                """)
                value { ctx ->
                    val bcRid = ctx.chainCtx.blockchainRid
                    Rt_ByteArrayValue.get(bcRid.toByteArray())
                }
            }

            property("args", C_NsProperty_ChainContext_Args, since = "0.7.0") {
                comment("""
                    The module argument values for the current module.

                    Arguments are defined for a module by a struct in that module with name `module_args`.

                    The values of those arguments are set in the Rell DApp's `chromia.yml` configuration.
                """)
            }
        }
    }
}

private object C_NsProperty_ChainContext_Args: C_NamespaceProperty() {
    override fun toExpr(ctx: C_NamespacePropertyContext, name: C_QualifiedName): V_Expr {
        val struct = ctx.modCtx.getModuleArgsStruct()
        if (struct == null) {
            val nameStr = name.str()
            throw C_Error.stop(name.pos, "expr_chainctx_args_norec",
                "To use '$nameStr', define a struct '${C_Constants.MODULE_ARGS_STRUCT}'")
        }

        val ideLink = struct.ideInfo.link
        if (ideLink != null) {
            ctx.exprCtx.nameCtx.setLink(name.last.pos, ideLink)
        }

        val moduleName = ctx.modCtx.moduleName
        val rFn = FnArgs(moduleName)
        val varId = C_ModuleArgsVarId(moduleName)

        return C_ExprUtils.createSysGlobalPropExpr(
            ctx.exprCtx,
            struct.structDef.type,
            rFn,
            name,
            pure = true,
            varId = varId,
        )
    }

    private class FnArgs(private val moduleName: R_ModuleName): R_SysFunctionEx_N() {
        override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
            checkEquals(args.size, 0)
            val res = ctx.appCtx.getModuleArgs(moduleName)
            return res ?: throw Rt_Exception.common(
                "chain_context.args:no_module_args:$moduleName",
                "No module args for module '$moduleName'",
            )
        }
    }

    private data class C_ModuleArgsVarId(private val moduleName: R_ModuleName): C_VarId() {
        override fun nameMsg() = "${moduleName.str()}:chain_context.args"
    }
}
