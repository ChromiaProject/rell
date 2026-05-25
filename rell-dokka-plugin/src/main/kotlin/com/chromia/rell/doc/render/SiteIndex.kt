/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.*

/**
 * Pre-computed lookup map keyed on qualified name (e.g. `crypto.privkey_to_pubkey`, `integer`,
 * `lib.lib1.user.name`). The renderer resolves markdown `\[ref]` link targets through this.
 *
 * Collisions can happen across `Doc_Module`s (system lib + user dapp) — first-write-wins is
 * fine: identical qualified names in both halves of the site address the same Rell symbol.
 */
internal class SiteIndex private constructor(
    private val byQname: Map<String, Entry>,
) {
    data class Entry(
        val module: Doc_Module,
        val pkg: Doc_Package,
        /** Non-null for top-level defs; null for the package itself. */
        val def: Doc_Def?,
        /** Non-null when `def` is a member of a `Doc_Class`. */
        val ownerClass: Doc_Class?,
    ) {
        fun href(): String = when {
            def == null -> Paths.packageIndexPath(module, pkg)
            ownerClass != null -> Paths.memberRelativePath(module, pkg, ownerClass, def)
            else -> Paths.pageRelativePath(module, pkg, def)
        }
    }

    fun resolve(qname: String): Entry? = byQname[qname]

    fun resolveAny(name: String, currentPackage: Doc_Package?): Entry? {
        if (currentPackage != null) {
            val qualified = "${currentPackage.qname}.$name"
            byQname[qualified]?.let { return it }
        }
        return byQname[name]
    }

    companion object {
        fun build(site: Doc_Site): SiteIndex {
            val byQname = mutableMapOf<String, Entry>()
            for (module in site.modules) {
                for (pkg in module.packages) {
                    byQname.putIfAbsent(pkg.qname, Entry(module, pkg, def = null, ownerClass = null))
                    for (def in pkg.defs) {
                        byQname.putIfAbsent(def.qname, Entry(module, pkg, def, ownerClass = null))
                        if (def is Doc_Class) {
                            for (member in def.members) {
                                byQname.putIfAbsent(member.qname, Entry(module, pkg, member, ownerClass = def))
                            }
                        }
                    }
                }
            }
            return SiteIndex(byQname)
        }
    }
}
