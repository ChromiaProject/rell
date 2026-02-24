/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_GraphUtils
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*

class C_Struct(
    val name: C_Name,
    val ideInfo: C_IdeSymbolInfo,
    val structDef: R_StructDefinition,
    val attrsGetter: C_LateGetter<List<C_CompiledAttribute>>,
)

object C_StructUtils {
    fun validateModuleArgs(msgCtx: C_MessageContext, s: C_Struct) {
        val rStructDef = s.structDef
        val typeFlags = rStructDef.struct.flags.typeFlags
        val name = rStructDef.moduleLevelName

        val attrsGtv = validateModuleArgsAttrs(msgCtx, s) { cAttr ->
            if (cAttr.rAttr.type.completeFlags().gtv.fromGtv) null else {
                C_CodeMsg("module_args:attr:no_gtv", "has a non-Gtv-compatible type (${cAttr.rAttr.type.str()})")
            }
        }

        if (!attrsGtv) {
            return
        }

        if (!typeFlags.gtv.fromGtv) {
            msgCtx.error(s.name.pos, "module_args:nogtv:${rStructDef.appLevelName}", "Struct '$name' is not Gtv-compatible")
            return
        }

        val attrsImmutable = validateModuleArgsAttrs(msgCtx, s) { cAttr ->
            if (cAttr.rAttr.mutable) {
                C_CodeMsg("module_args:attr:mutable", "is mutable")
            } else if (cAttr.rAttr.type.completeFlags().mutable) {
                C_CodeMsg("module_args:attr:mutable_type", "has a mutable type (${cAttr.rAttr.type.str()})")
            } else {
                null
            }
        }

        if (!attrsImmutable) {
            return
        }

        if (typeFlags.mutable) {
            msgCtx.error(s.name.pos, "module_args:mutable:${rStructDef.appLevelName}", "Struct '$name' is mutable")
            return
        }

        val attrsPure = validateModuleArgsAttrs(msgCtx, s) { cAttr ->
            if (cAttr.rAttr.type.completeFlags().pure) null else {
                C_CodeMsg("module_args:attr:not_pure", "has a bad type (${cAttr.rAttr.type.str()})")
            }
        }

        if (!attrsPure) {
            return
        }

        if (!typeFlags.pure) {
            msgCtx.error(s.name.pos, "module_args:node_pure:${rStructDef.appLevelName}",
                    "Struct '$name' has an attribute of a bad type")
        }
    }

    private fun validateModuleArgsAttrs(
            msgCtx: C_MessageContext,
            s: C_Struct,
            checker: (C_CompiledAttribute) -> C_CodeMsg?
    ): Boolean {
        var res = true

        for (cAttr in s.attrsGetter.get()) {
            val codeMsg = checker(cAttr)
            codeMsg ?: continue
            val pos = cAttr.defPos ?: s.name.pos
            val code = "${codeMsg.code}:${s.structDef.appLevelName}:${cAttr.rAttr.name}"
            val msg = "Attribute '${cAttr.rAttr.name}' of struct ${s.structDef.simpleName} ${codeMsg.msg}"
            msgCtx.error(pos, code, msg)
            res = false
        }

        return res
    }
}

class C_StructsInfo(
    val mutable: ImmSet<R_Struct>,
    val nonVirtualable: ImmSet<R_Struct>,
    val nonPure: ImmSet<R_Struct>,
    val nonGtvFrom: ImmSet<R_Struct>,
    val nonGtvTo: ImmSet<R_Struct>,
    val graph: ImmMap<R_Struct, List<R_Struct>>
)

object C_StructGraphUtils {
    fun processStructs(structs: List<R_Struct>) {
        val info = buildStructsInfo(structs)
        val graph = info.graph

        val transGraph = C_GraphUtils.transpose(graph)
        val transGraphFn: (R_Struct) -> Collection<R_Struct> = transGraph::getValue

        val cyclicStructs = C_GraphUtils.findCyclicVertices(graph).toSet()
        val infiniteStructs = C_GraphUtils.closure(cyclicStructs, transGraphFn).toSet()
        val mutableStructs = C_GraphUtils.closure(info.mutable, transGraphFn).toSet()
        val nonVirtualStructs = C_GraphUtils.closure(info.nonVirtualable, transGraphFn).toSet()
        val nonPureStructs = C_GraphUtils.closure(info.nonPure, transGraphFn).toSet()
        val nonGtvFromStructs = C_GraphUtils.closure(info.nonGtvFrom, transGraphFn).toSet()
        val nonGtvToStructs = C_GraphUtils.closure(info.nonGtvTo, transGraphFn).toSet()

        for (struct in structs) {
            val gtv = R_GtvCompatibility(struct !in nonGtvFromStructs, struct !in nonGtvToStructs)

            val typeFlags = R_TypeFlags(
                pure = struct !in nonPureStructs,
                mutable = struct in mutableStructs,
                gtv = gtv,
                virtualable = struct !in nonVirtualStructs,
                mixedTuple = false,
                hasTypeVariable = false,
            )

            val flags = R_StructFlags(
                typeFlags = typeFlags,
                cyclic = struct in cyclicStructs,
                infinite = struct in infiniteStructs,
            )

            struct.setFlags(flags)
        }
    }

    private fun buildStructsInfo(structs: Collection<R_Struct>): C_StructsInfo {
        val declaredStructs = structs.toImmSet()
        val infoMap = structs.associateWith { calcStructInfo(declaredStructs, it.type) }
        val graph = infoMap.mapValuesToImmMap { (_, v) -> v.dependencies.toList() }

        val mutable = infoMap.filter { (_, v) -> v.directFlags.mutable }.keys.toImmSet()
        val nonVirtual = infoMap.filter { (_, v) -> !v.directFlags.virtualable }.keys.toImmSet()
        val nonPure = infoMap.filter { (_, v) -> !v.directFlags.pure }.keys.toImmSet()
        val nonGtvFrom = infoMap.filter { (_, v) -> !v.directFlags.gtv.fromGtv }.keys.toImmSet()
        val nonGtvTo = infoMap.filter { (_, v) -> !v.directFlags.gtv.toGtv }.keys.toImmSet()

        return C_StructsInfo(
                mutable = mutable,
                nonVirtualable = nonVirtual,
                nonPure = nonPure,
                nonGtvFrom = nonGtvFrom,
                nonGtvTo = nonGtvTo,
                graph = graph
        )
    }

    private fun calcStructInfo(declaredStructs: Set<R_Struct>, type: R_Type): StructInfo {
        val flags = mutableListOf(type.directFlags())
        val deps = mutableSetOf<R_Struct>()

        for (subType in type.componentTypes()) {
            val subStruct = discoverStructInfo(declaredStructs, subType)
            flags.add(subStruct.directFlags)
            deps.addAll(subStruct.dependencies)
        }

        val resFlags = R_TypeFlags.combine(flags)
        return StructInfo(resFlags, deps.toImmSet())
    }

    private fun discoverStructInfo(declaredStructs: Set<R_Struct>, type: R_Type): StructInfo {
        // Taking into account only structs declared in this app (not those compiled elsewhere).
        if (type is R_StructType && type.struct in declaredStructs) {
            return StructInfo(type.directFlags(), immSetOf(type.struct))
        }
        return calcStructInfo(declaredStructs, type)
    }

    private class StructInfo(val directFlags: R_TypeFlags, val dependencies: ImmSet<R_Struct>)
}
