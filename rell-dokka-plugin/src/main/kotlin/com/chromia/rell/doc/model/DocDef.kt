/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.model

/**
 * Sealed hierarchy describing a single documented declaration. Every concrete subtype is what
 * ends up on a `<def>.html` (or, for classlike types, `<Type>/index.html`) page.
 *
 * Cross-package addressing uses `qname` (fully-qualified Rell name, dot-separated). The renderer
 * builds an index keyed on `qname` and resolves `\[ref]`-style doc-comment links through it.
 */
internal sealed interface Doc_Def {
    val name: String
    val qname: String
    val docMd: String
    val source: Doc_Source?
    val deprecated: Doc_Deprecated?
}

/** Position of a definition in source — drives the source-link rewrite. */
internal data class Doc_Source(
    /** Path relative to the project root passed on the command line. */
    val path: String,
    /** 1-based line number, or null if unknown. */
    val line: Int?,
)

internal data class Doc_Deprecated(
    val message: String,
    val forRemoval: Boolean,
)

internal enum class Doc_FunctionKind(val keyword: String) {
    FUNCTION("function"),
    OPERATION("operation"),
    QUERY("query"),
    CONSTRUCTOR("constructor"),
    SPECIAL_CONSTRUCTOR("constructor"),
}

internal data class Doc_Function(
    override val name: String,
    override val qname: String,
    override val docMd: String,
    override val source: Doc_Source?,
    override val deprecated: Doc_Deprecated?,
    val kind: Doc_FunctionKind,
    val params: List<Doc_Param>,
    val returnType: Doc_Type?,
    val typeParams: List<Doc_TypeParam>,
    /** True for stdlib `pure` functions. */
    val pure: Boolean = false,
    /** True for stdlib static methods on a type. */
    val static: Boolean = false,
    /** True for `@extendable function`. */
    val extendable: Boolean = false,
    /** Non-null for `@extend(target) function` — fully-qualified name of the target. */
    val extendTargetQname: String? = null,
    /** Non-null when the routine carries an explicit `@mount(...)` name distinct from its simple name. */
    val mountName: String? = null,
    /** Alias-only: the qualified name of the function this is an alias of. */
    val aliasOfQname: String? = null,
    /** True for anonymous function (Rell `function#0`-style). */
    val anonymous: Boolean = false,
    /** True when the parameters' positions show as `[name: T]` (M_ParamArity.ZERO_ONE). */
    val zeroOneArity: Boolean = false,
    /** True when this is a stdlib hidden function. */
    val hidden: Boolean = false,
    /**
     * Additional overloads sharing this function's `name`, `qname`, and `kind`. Empty for
     * non-overloaded functions. The primary `Doc_Function` plus `overloads` form the complete
     * set; only the primary participates in routing / search / navigation, but every entry
     * (primary + overloads) renders its own signature on the function's page.
     */
    val overloads: List<Doc_Function> = emptyList(),
) : Doc_Def

internal data class Doc_Param(
    val name: String,
    val type: Doc_Type,
    val docMd: String,
    val zeroOne: Boolean = false,
    val vararg: Boolean = false,
)

internal data class Doc_TypeParam(
    val name: String,
    /** Optional textual bound, e.g. `: Map<K, V>`. Pre-rendered as a string here. */
    val bound: Doc_Type? = null,
)

internal data class Doc_Property(
    override val name: String,
    override val qname: String,
    override val docMd: String,
    override val source: Doc_Source?,
    override val deprecated: Doc_Deprecated?,
    val type: Doc_Type,
    val mutable: Boolean = false,
    val key: Boolean = false,
    val index: Boolean = false,
    /** Pre-formatted literal source text (e.g. `123`, `"hello"`, `null`). */
    val defaultValueText: String? = null,
    val aliasOfQname: String? = null,
) : Doc_Def

internal enum class Doc_ClassKind(val keyword: String) {
    ENTITY("entity"),
    OBJECT("object"),
    STRUCT("struct"),
    ENUM("enum"),
    /** Stdlib type — has constructors / static methods / static constants. */
    TYPE("type"),
}

internal data class Doc_Class(
    override val name: String,
    override val qname: String,
    override val docMd: String,
    override val source: Doc_Source?,
    override val deprecated: Doc_Deprecated?,
    val kind: Doc_ClassKind,
    val typeParams: List<Doc_TypeParam>,
    val superTypes: List<Doc_Type>,
    val members: List<Doc_Def>,
    /** Enum entries (only meaningful for ENUM). */
    val entries: List<String> = emptyList(),
    val hidden: Boolean = false,
    val abstract: Boolean = false,
) : Doc_Def

/** Stdlib top-level alias for a type (`name` → `text`). */
internal data class Doc_TypeAlias(
    override val name: String,
    override val qname: String,
    override val docMd: String,
    override val source: Doc_Source?,
    override val deprecated: Doc_Deprecated?,
    val targetQname: String,
) : Doc_Def
