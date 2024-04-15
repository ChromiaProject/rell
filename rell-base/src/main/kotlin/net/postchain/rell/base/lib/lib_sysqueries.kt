/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.core.C_CompilerExecutor
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_SysFunctionEx_N
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_RellVersionProperty
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf

// Not a normal library, only provides queries that are not bound to a namespace, but are accessible via their mount names.

object Lib_SysQueries {
    fun createQueries(executor: C_CompilerExecutor): List<R_QueryDefinition> {
        return immListOf(
            C_Utils.createSysQuery(executor, "get_rell_version", R_TextType, SysQueryFns.GetRellVersion),
            C_Utils.createSysQuery(executor, "get_postchain_version", R_TextType, SysQueryFns.GetPostchainVersion),
            C_Utils.createSysQuery(executor, "get_build", R_TextType, SysQueryFns.GetBuild),
            C_Utils.createSysQuery(executor, "get_build_details", SysQueryFns.GetBuildDetails.TYPE, SysQueryFns.GetBuildDetails),
            C_Utils.createSysQuery(executor, "get_app_structure", R_GtvType, SysQueryFns.GetAppStructure)
        )
    }
}

private object SysQueryFns {
    object GetRellVersion: R_SysFunctionEx_N() {
        override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
            checkEquals(args.size, 0)
            return Rt_TextValue.get(RellVersions.VERSION_STR)
        }
    }

    object GetPostchainVersion: R_SysFunctionEx_N() {
        override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
            checkEquals(args.size, 0)
            val ver = ctx.globalCtx.rellVersion()
            val postchainVer = ver.properties.getValue(Rt_RellVersionProperty.POSTCHAIN_VERSION)
            return Rt_TextValue.get(postchainVer)
        }
    }

    object GetBuild: R_SysFunctionEx_N() {
        override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
            checkEquals(args.size, 0)
            val ver = ctx.globalCtx.rellVersion()
            return Rt_TextValue.get(ver.buildDescriptor)
        }
    }

    object GetBuildDetails: R_SysFunctionEx_N() {
        val TYPE = R_MapType(R_TextType, R_TextType)

        override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
            checkEquals(args.size, 0)
            val ver = ctx.globalCtx.rellVersion()
            return Rt_MapValue(TYPE, ver.rtProperties.toMutableMap())
        }
    }

    object GetAppStructure: R_SysFunctionEx_N() {
        override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
            checkEquals(args.size, 0)
            val v = ctx.appCtx.app.toMetaGtv()
            return Rt_GtvValue.get(v)
        }
    }
}
