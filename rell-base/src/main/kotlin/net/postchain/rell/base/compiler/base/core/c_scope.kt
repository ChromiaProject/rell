/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.base.namespace.C_Namespace
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceEntry
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceMember
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceMemberTag
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion

class C_ScopeBuilder {
    private val scope: C_Scope

    constructor(): this(null, { C_Namespace.EMPTY })

    private constructor(parentScope: C_Scope?, nsGetter: Getter<C_Namespace>) {
        this.scope = C_Scope(parentScope, nsGetter)
    }

    fun nested(nsGetter: Getter<C_Namespace>): C_ScopeBuilder {
        return C_ScopeBuilder(scope, nsGetter)
    }

    fun scope() = scope
}

class C_Scope(
    private val parent: C_Scope?,
    private val nsGetter: Getter<C_Namespace>,
) {
    private val rootNs: C_Namespace by lazy {
        nsGetter()
    }

    fun findEntry(name: R_Name, tags: List<C_NamespaceMemberTag>): C_NamespaceEntry? {
        var scope: C_Scope? = this
        var res: C_NamespaceEntry? = null

        while (scope != null) {
            val entry = scope.rootNs.getEntry(name)
            if (entry != null) {
                if (entry.hasTag(tags)) {
                    res = entry
                    break
                } else if (res == null) {
                    res = entry
                }
            }
            scope = scope.parent
        }

        return res
    }

    fun ideCompletions(compilerOptions: C_CompilerOptions): ImmMultimap<String, IdeCompletion> {
        val res = mutableMultimapOf<String, IdeCompletion>()
        val set = mutableSetOf<Pair<R_Name, C_NamespaceEntry>>()
        var scope: C_Scope? = this

        while (scope != null) {
            for ((name, entry) in scope.rootNs.getEntries()) {
                // A parent scope may contain same entries as a child scope, e.g. child is a file, parent is a module.
                if (set.add(name to entry)) {
                    val members = entry.directMembers.ifEmpty { entry.importMembers }
                    ideCompletionsProcessMembers(name, members, res, compilerOptions)
                }
            }
            scope = scope.parent
        }

        return res.toImmMultimap()
    }

    private fun ideCompletionsProcessMembers(
        name: R_Name,
        members: List<C_NamespaceMember>,
        res: Multimap<String, IdeCompletion>,
        compilerOptions: C_CompilerOptions,
    ) {
        for (member in members) {
            if (!member.restrictions.isRestricted(compilerOptions)) {
                res.putAll(name.str, member.ideCompletions)
            }
        }
    }

    fun ideCompletionsDirect(
        executor: C_CompilerExecutor,
        compilerOptions: C_CompilerOptions,
    ): C_LateGetter<Multimap<String, IdeCompletion>> {
        val late = C_LateInit(C_CompilerPass.APPLICATION, immMultimapOf<String, IdeCompletion>())
        executor.onPass(C_CompilerPass.APPLICATION) {
            val res = mutableMultimapOf<String, IdeCompletion>()
            var scope: C_Scope? = this
            while (scope != null) {
                for ((name, entry) in scope.rootNs.getEntries()) {
                    val members = entry.directMembers.ifEmpty { entry.importMembers }
                    for (member in members) {
                        if (!member.restrictions.isRestricted(compilerOptions)) {
                            res.putAll(name.str, member.ideCompletions)
                        }
                    }
                }
                scope = scope.parent
            }
            late.set(res.toImmMultimap())
        }
        return late.getter
    }
}
