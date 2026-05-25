/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.render

import com.chromia.rell.doc.model.Doc_Class
import com.chromia.rell.doc.model.Doc_Def
import com.chromia.rell.doc.model.Doc_Module
import com.chromia.rell.doc.model.Doc_Package

/**
 * Slug & filename helpers — `Paths.fileSlug("My Dapp")` returns `"-my -dapp"`, matching Dokka's
 * built-in package-name mangling. Downstream tooling (chromia-cli IT tests, docs.chromia.com)
 * already addresses the existing slugs, so the same rule is preserved verbatim.
 *
 * Rules:
 *  - lowercase letters & digits stay as-is
 *  - each uppercase ASCII letter is prefixed with `-` and lowercased
 *  - spaces, dots, dashes, underscores, brackets stay as-is
 *  - the `function#N` anonymous form escapes `#` to `%23` so files can be referenced over HTTP
 */
internal object Paths {
    fun fileSlug(name: String): String = buildString {
        for (c in name) when {
            c in 'A'..'Z' -> { append('-'); append(c + 32) }
            else -> append(c)
        }
    }

    /** Escape `#` (anonymous functions: `function#0` → `function%230`). */
    fun urlEncodeName(name: String): String = name.replace("#", "%23")

    fun packageDir(pkg: Doc_Package): String = pkg.qname.ifEmpty { "[root]" }

    /**
     * For a top-level def (function/property/typealias) the file lives at
     * `<module>/<package>/<def>.html`. For a class-like the file is `<module>/<package>/<Type>/index.html`.
     */
    fun pageRelativePath(module: Doc_Module, pkg: Doc_Package, def: Doc_Def): String {
        val base = "${module.slug}/${packageDir(pkg)}"
        val slug = urlEncodeName(fileSlug(def.name))
        return if (def is Doc_Class) "$base/$slug/index.html" else "$base/$slug.html"
    }

    /**
     * For a class-like's member (attribute / method / static) — `<module>/<package>/<Type>/<member>.html`.
     */
    fun memberRelativePath(module: Doc_Module, pkg: Doc_Package, owner: Doc_Class, member: Doc_Def): String {
        val ownerDir = urlEncodeName(fileSlug(owner.name))
        val memberSlug = urlEncodeName(fileSlug(member.name))
        return "${module.slug}/${packageDir(pkg)}/$ownerDir/$memberSlug.html"
    }

    fun packageIndexPath(module: Doc_Module, pkg: Doc_Package): String =
        "${module.slug}/${packageDir(pkg)}/index.html"

    fun moduleIndexPath(module: Doc_Module): String = "${module.slug}/index.html"
}
