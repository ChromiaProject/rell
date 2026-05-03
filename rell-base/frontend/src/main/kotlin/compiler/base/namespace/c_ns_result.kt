/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.namespace

import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.module.C_ModuleKey
import net.postchain.rell.base.utils.*

object C_NsRes_ResultMaker {
    fun make(modules: Map<C_ModuleKey, C_NsImp_Namespace>): Map<C_ModuleKey, C_Namespace> {
        val (impList, listVsMap) = ListVsMap.mapToList(modules)
        val nsList = make0(impList)
        val nsMap = listVsMap.listToMap(nsList)
        return nsMap
    }

    fun make(impNs: C_NsImp_Namespace): C_Namespace {
        val nsList = make0(listOf(impNs))
        checkEquals(nsList.size, 1)
        return nsList[0]
    }

    private fun make0(impList: List<C_NsImp_Namespace>): ImmList<C_Namespace> {
        val maker = C_NsRes_InternalMaker()
        val nsList = impList.mapToImmList { impNs -> maker.makeModule(impNs) }
        return nsList
    }
}

private class C_NsRes_InternalMaker {
    private val nsMap = mutableMapOf<C_NsImp_Namespace, C_Namespace>()
    private val nsMemberMap = mutableMapOf<C_NsImp_Def_Namespace, C_NamespaceMember>()

    fun makeModule(ns: C_NsImp_Namespace): C_Namespace {
        val res = makeNamespace(ns)
        return res
    }

    private fun makeNamespace(ns: C_NsImp_Namespace): C_Namespace {
        val lateNs = nsMap[ns]
        if (lateNs != null) return lateNs

        var init by LateInit<C_Namespace>()
        val lateNs2 = C_Namespace.makeLate { init }
        nsMap[ns] = lateNs2

        val resNs = makeNamespace0(ns)
        init = resNs

        return lateNs2
    }

    private fun makeNamespace0(ns: C_NsImp_Namespace): C_Namespace {
        val b = C_NamespaceBuilder()
        val names = ns.directDefs.keys + ns.importDefs.keySet()

        for (name in names) {
            val directDef = ns.directDefs[name]
            val importDefs = ns.importDefs.get(name).orEmpty()
            val elem = makeDef(directDef, importDefs)
            b.add(name, elem)
        }

        return b.build()
    }

    private fun makeDef(directDef: C_NsImp_Def?, importDefs: Collection<C_NsImp_Def>): C_NamespaceEntry {
        val directMember = if (directDef == null) null else makeMember0(directDef)
        val importMembers = importDefs.mapToImmList { makeMember0(it) }
        return C_NamespaceEntry(immListOfNotNull(directMember), importMembers)
    }

    private fun makeMember0(def: C_NsImp_Def): C_NamespaceMember {
        return when (def) {
            is C_NsImp_Def_Simple -> def.member
            is C_NsImp_Def_Namespace -> {
                val impNs = def.ns()
                val ns = makeNamespace(impNs)
                nsMemberMap.getOrPut(def) {
                    val decType = C_DeclarationType.NAMESPACE
                    val restrictions = C_MemberRestrictions.makeUser(def.defName, decType, def.deprecated)
                    val base = C_NamespaceMemberBase(def.defName, def.ideInfo, restrictions)
                    C_NamespaceMember_Namespace(base, ns, def.importModule)
                }
            }
        }
    }
}
