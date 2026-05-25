/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.model

/**
 * Structured type for the renderer. Two pieces of information drive every leaf:
 *  - the surface text we print (used for `<code>` content),
 *  - the qualified name we can look up in the site index to make a hyperlink (or null when
 *    there's nothing to link, e.g. type parameters and tuple fields).
 */
internal sealed interface Doc_Type {
    /** A named type, possibly with generic arguments. `qname` is non-null when linkable. */
    data class Named(
        val text: String,
        val qname: String?,
        val args: List<Arg> = emptyList(),
    ) : Doc_Type

    /** Anonymous tuple — fields may be named (`(a: integer, b: text)`) or positional. */
    data class Tuple(val fields: List<Field>) : Doc_Type {
        data class Field(val name: String?, val type: Doc_Type)
    }

    /** Function type `(P1, P2) -> R`. */
    data class Function(val params: List<Doc_Type>, val result: Doc_Type) : Doc_Type

    /** `T?` */
    data class Nullable(val inner: Doc_Type) : Doc_Type

    /** `T` — references a type parameter; not linkable. */
    data class TypeParam(val text: String) : Doc_Type

    /** Catch-all for raw text the runtime gave us but we can't structure further. */
    data class Raw(val text: String) : Doc_Type

    sealed interface Arg {
        /** Plain `T` (invariant). */
        data class Invariant(val type: Doc_Type) : Arg

        /** `-T` — `M_TypeSet_SubOf`. */
        data class SubOf(val type: Doc_Type) : Arg

        /** `+T` — `M_TypeSet_SuperOf`. */
        data class SuperOf(val type: Doc_Type) : Arg

        /** `*` */
        data object Star : Arg
    }
}
