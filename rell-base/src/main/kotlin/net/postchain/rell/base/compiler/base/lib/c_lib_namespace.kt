/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceItem
import net.postchain.rell.base.compiler.base.namespace.C_NsMemberFactory
import net.postchain.rell.base.compiler.base.namespace.C_SysNsProto
import net.postchain.rell.base.compiler.base.namespace.C_SysNsProtoBuilder
import net.postchain.rell.base.compiler.base.utils.C_RFullNamePath
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.mutableMultimapOf
import net.postchain.rell.base.utils.toImmMap
import net.postchain.rell.base.utils.toImmSet

class C_LibNamespace private constructor(
    private val namePath: C_RFullNamePath,
    private val namespaces: Map<R_Name, C_LibNestedNamespace>,
    private val members: Map<R_Name, C_NamespaceItem>,
) {
    fun toSysNsProto(): C_SysNsProto {
        val b = C_SysNsProtoBuilder()

        for ((name, member) in members) {
            b.addMember(name, member)
        }

        val memberFactory = C_NsMemberFactory(namePath)
        for ((name, libNs) in namespaces) {
            libNs.toSysNsProto(b, memberFactory, name)
        }

        return b.build()
    }

    abstract class Maker(val basePath: C_RFullNamePath) {
        abstract fun addMember(name: R_Name, member: C_NamespaceItem)
        abstract fun addFunction(name: R_Name, fnCase: C_LibFuncCase<V_GlobalFunctionCall>)

        abstract fun addNamespace(
            name: R_Name,
            ideInfo: C_IdeSymbolInfo,
            restrictions: C_MemberRestrictions,
            block: (Maker) -> Unit,
        )
    }

    class Builder private constructor(
        basePath: C_RFullNamePath,
        private var active: Boolean,
    ): Maker(basePath) {
        constructor(basePath: C_RFullNamePath): this(basePath, active = true)

        private var done = false

        private val members = mutableMapOf<R_Name, C_NamespaceItem>()
        private val functions = mutableMultimapOf<R_Name, C_LibFuncCase<V_GlobalFunctionCall>>()
        private val namespaces = mutableMapOf<R_Name, NestedBuilder>()

        override fun addMember(name: R_Name, member: C_NamespaceItem) {
            check(active)
            check(!done)
            checkNameConflict(name, members, namespaces, functions.asMap())
            members[name] = member
        }

        override fun addFunction(name: R_Name, fnCase: C_LibFuncCase<V_GlobalFunctionCall>) {
            check(active)
            check(!done)
            checkNameConflict(name, members, namespaces)
            functions.put(name, fnCase)
        }

        override fun addNamespace(
            name: R_Name,
            ideInfo: C_IdeSymbolInfo,
            restrictions: C_MemberRestrictions,
            block: (Maker) -> Unit,
        ) {
            check(active)
            check(!done)

            var ns = namespaces[name]
            if (ns == null) {
                checkNameConflict(name, members, functions.asMap())
                val builder = Builder(basePath.append(name), active = false)
                ns = NestedBuilder(builder, ideInfo, restrictions)
                namespaces[name] = ns
            }

            check(!ns.builder.active)
            check(!ns.builder.done)
            ns.builder.active = true
            active = false

            block(ns.builder)

            ns.builder.active = false
            active = true
        }

        private fun checkNameConflict(name: R_Name, vararg maps: Map<R_Name, *>) {
            check(maps.isNotEmpty())
            val conflict = maps.any { name in it }
            check(!conflict) {
                val fullName = basePath.fullName(name)
                "Name conflict: ${fullName.str()}"
            }
        }

        fun build(): C_LibNamespace {
            check(!done)
            done = true

            val resNamespaces = namespaces
                .mapValues { it.value.build() }
                .toImmMap()

            val memberFactory = C_NsMemberFactory(basePath)
            val fnMembers = functions.asMap().mapValues { (name, cases) ->
                createFunctionMember(name, cases.toList(), memberFactory)
            }

            val simpleMembers = members.mapValues { it.value }

            val resMembers = fnMembers + simpleMembers
            return C_LibNamespace(basePath, resNamespaces.toImmMap(), resMembers.toImmMap())
        }

        private fun createFunctionMember(
            simpleName: R_Name,
            cases: List<C_LibFuncCase<V_GlobalFunctionCall>>,
            memberFactory: C_NsMemberFactory,
        ): C_NamespaceItem {
            val fullName = basePath.fullName(simpleName)
            val naming = C_MemberNaming.makeFullName(fullName)
            val fn = C_LibFunctionUtils.makeGlobalFunction(naming, cases)
            val ideInfo = cases.first().ideInfo
            val member = memberFactory.function(fullName.last, fn, ideInfo, C_MemberRestrictions.NULL)
            return C_NamespaceItem(member)
        }

        private class NestedBuilder(
            val builder: Builder,
            private val ideInfo: C_IdeSymbolInfo,
            private val restrictions: C_MemberRestrictions,
        ) {
            fun build(): C_LibNestedNamespace {
                val ns = builder.build()
                return C_LibNestedNamespace(ns, ideInfo, restrictions)
            }
        }
    }

    companion object {
        fun merge(namespaces: List<C_LibNamespace>): C_LibNamespace {
            check(namespaces.isNotEmpty())

            val single = namespaces.singleOrNull()
            if (single != null) {
                return single
            }

            val resPath = namespaces.first().namePath

            val namespaceNames = namespaces.flatMap { it.namespaces.keys }.toImmSet()
            val resNamespaces = namespaceNames.associateWith { name ->
                val mems = namespaces.mapNotNull { it.namespaces[name] }
                mergeNamespaces(mems)
            }

            val memberNames = namespaces.flatMap { it.members.keys }.toImmSet()
            val resMembers = memberNames.associateWith { name ->
                val mems = namespaces.mapNotNull { it.members[name] }
                val resMem = mems.singleOrNull()
                checkNotNull(resMem) { "Namespace member conflict: $resPath $name (${mems.size})" }
                resMem
            }

            return C_LibNamespace(resPath, resNamespaces, resMembers)
        }

        private fun mergeNamespaces(members: List<C_LibNestedNamespace>): C_LibNestedNamespace {
            check(members.isNotEmpty())

            val single = members.singleOrNull()
            if (single != null) {
                return single
            }

            val namespaces = members.map { it.namespace }
            val resNamespace = merge(namespaces)

            val resIdeInfo = members.first().ideInfo
            val resRestrictions = members.first().restrictions //TODO merge restrictions
            return C_LibNestedNamespace(resNamespace, resIdeInfo, resRestrictions)
        }
    }
}

private class C_LibNestedNamespace(
    val namespace: C_LibNamespace,
    val ideInfo: C_IdeSymbolInfo,
    val restrictions: C_MemberRestrictions,
) {
    fun toSysNsProto(b: C_SysNsProtoBuilder, memberFactory: C_NsMemberFactory, name: R_Name) {
        val ns = namespace.toSysNsProto().toNamespace()
        val member = memberFactory.namespace(name, ns, ideInfo, restrictions)
        b.addMember(name, member)
    }
}
