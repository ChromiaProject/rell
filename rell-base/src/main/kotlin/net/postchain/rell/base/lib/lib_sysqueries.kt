/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.builder.GtvBuilder
import net.postchain.rell.base.compiler.base.core.C_CompilerExecutor
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Exception
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
            C_Utils.createSysQuery(executor, "get_app_structure", R_GtvType, SysQueryFns.GetAppStructure),
            C_Utils.createSysQuery(executor, "get_mount_names", R_GtvType, SysQueryFns.GetMountNames, SysQueryFns.GetMountNames.PARAMS),
            C_Utils.createSysQuery(executor, "get_module_args", R_GtvType, SysQueryFns.GetModuleArgs, SysQueryFns.GetModuleArgs.PARAMS),
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

    object GetMountNames: R_SysFunctionEx_N() {
        val PARAMS = immListOf(
            R_FunctionParam(R_Name.of("kinds"), R_ListType(R_TextType)),
            R_FunctionParam(R_Name.of("modules"), R_ListType(R_TextType)),
        )

        val ALLOWED_KINDS = immListOf("query", "operation", "entity", "object")

        override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
            checkEquals(args.size, 2)
            val kindsArg = args[0].asList().map { it.asString() }.toSet()
            val modulesArg = args[1].asList().map { R_ModuleName.ofOpt(it.asString()) }.toSet()
            if (kindsArg.isNotEmpty() && kindsArg.any { it !in ALLOWED_KINDS }) {
                val invalidKinds = kindsArg.filterNot { it in ALLOWED_KINDS }
                throw Rt_Exception.common("rell.get_mount_names:bad_kind:${invalidKinds.joinToString(",")}",
                    "Invalid kind(s): $invalidKinds. Supported kinds are $ALLOWED_KINDS")
            }
            if (modulesArg.isNotEmpty() && modulesArg.any { it == null }) {
                val invalidModules = args[1].asList().filter { R_ModuleName.ofOpt(it.asString()) == null }.map { it.str() }
                throw Rt_Exception.common("rell.get_mount_names:bad_module:${invalidModules.joinToString(",")}",
                    "Invalid module name(s): $invalidModules.")
            }

            return GtvBuilder().apply {
                ctx.appCtx.app.moduleMap
                    .filterKeys { modulesArg.containsOrEmpty(it) }
                    .values
                    .forEach { module ->
                        addMountNames(kindsArg, "query", "queries", module.queries) { it.mountName }
                        addMountNames(kindsArg, "operation", "operations", module.operations) { it.mountName }
                        addMountNames(kindsArg, "entity", "entities", module.entities) { it.mountName }
                        addMountNames(kindsArg, "object", "objects", module.objects) { it.rEntity.mountName }
                    }
                }.let { Rt_GtvValue.get(it.build()) }
        }

        private fun <T: R_Definition> GtvBuilder.addMountNames(
                kindsArg: Set<String>,
                kind: String,
                resultKey: String,
                definitions: Map<*, T>,
                mountNameGetter: (T) -> R_MountName,
                )
        {
            if (!kindsArg.containsOrEmpty(kind)) return
            update(definitions.values.map { mountNameGetter(it) }.toGtvArrayNode(), resultKey)
        }

        private fun Collection<R_MountName>.toGtvArrayNode(): GtvBuilder.GtvArrayNode {
            val valueNodes = map { GtvBuilder.GtvNode.decode(gtv(it.str())) }
            return GtvBuilder.GtvArrayNode(valueNodes, GtvBuilder.GtvArrayMerge.APPEND)
        }
    }

    object GetModuleArgs: R_SysFunctionEx_N() {
        val PARAMS = immListOf(
            R_FunctionParam(R_Name.of("modules"), R_ListType(R_TextType)),
        )

        override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
            val (modules, invalidModules) = validateArgs(args)
            if (invalidModules.isNotEmpty()) {
                throw Rt_Exception.common(
                    "rell.get_module_args:bad_module:${invalidModules.joinToString(",")}",
                    "Invalid module name(s): $invalidModules."
                )
            }

            val moduleArgsMap = ctx.appCtx.app.moduleArgs
                .filterKeys { modules.containsOrEmpty(it) }
                .mapNotNull { (moduleName, structDef) ->
                    ctx.appCtx.getModuleArgs(moduleName)?.let { moduleArgs ->
                        val gtv = structDef.type.rtToGtv(moduleArgs, pretty = true)
                        moduleName.str() to gtv
                    }
                }.toMap()

            return Rt_GtvValue.get(gtv(moduleArgsMap))
        }

        private fun validateArgs(args: List<Rt_Value>): Pair<Set<R_ModuleName>, Set<String>> {
            checkEquals(args.size, 1)
            val modules = args[0].asList().map { it.asString() to R_ModuleName.ofOpt(it.asString()) }

            val validModules = modules.mapNotNull { it.second }.toSet()
            val invalidModules = modules.filter { it.second == null }.map { it.first }.toSet()

            return validModules to invalidModules
        }
    }

    private fun <T> Collection<T>.containsOrEmpty(v: T) = isEmpty() || v in this
}
