/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.tokens

import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind

enum class RellTokenModifier(
    val modifierStringId: String
) {
    READONLY("readonly"),
    MODIFICATION("modification"),

    // Rell keyword modifiers
    DEFAULT("rell-default"),
    MODULE("rell-module"),
    ANNOTATION("rell-annotation"),
    NAMESPACE("rell-namespace"),
    TYPE("rell-type"),
    ENUM("rell-enum"),
    ENUM_VALUE("rell-enum_value"),
    GLOBAL_CONSTANT("rell-global_constant"),
    ENTITY("rell-entity"),
    OBJECT("rell-object"),
    ENTITY_ATTR_NORMAL_VAL("rell-entity_attr_normal_val"),
    ENTITY_ATTR_NORMAL_VAR("rell-entity_attr_normal_var"),
    ENTITY_ATTR_KEYINDEX_VAL("rell-entity_attr_keyindex_val"),
    ENTITY_ATTR_KEYINDEX_VAR("rell-entity_attr_keyindex_var"),
    STRUCT("rell-struct"),
    STRUCT_ATTR_VAL("rell-struct_attr_val"),
    STRUCT_ATTR_VAR("rell-struct_attr_var"),
    TUPLE_ATTR("rell-tuple_attr"),
    OPERATION("rell-operation"),
    QUERY("rell-query"),
    FUNCTION("rell-function"),
    FUNCTION_EXTENDABLE("rell-extendable_function"),
    NAMED_ARGUMENT("rell-named_argument"),
    LOCAL_VAL("rell-local_val"),
    LOCAL_VAR("rell-local_var"),
    AT_ALIAS("rell-at_alias"),
    PARAMETER("rell-parameter"),
    CALL("rell-call"),
}

enum class RellTokenType(
    val tokenStringId: String,
    vararg modifiers: RellTokenModifier
) {
    DEFAULT("keyword", RellTokenModifier.DEFAULT),
    MODULE("namespace", RellTokenModifier.MODULE),
    ANNOTATION("decorator", RellTokenModifier.ANNOTATION),
    NAMESPACE("namespace", RellTokenModifier.NAMESPACE),
    TYPE("type", RellTokenModifier.TYPE),
    ENUM("enum", RellTokenModifier.ENUM),
    ENUM_VALUE("enumMember", RellTokenModifier.ENUM_VALUE),
    GLOBAL_CONSTANT("variable", RellTokenModifier.GLOBAL_CONSTANT, RellTokenModifier.READONLY),
    ENTITY("class", RellTokenModifier.ENTITY, RellTokenModifier.MODIFICATION),
    OBJECT("class", RellTokenModifier.OBJECT, RellTokenModifier.MODIFICATION),
    ENTITY_ATTR_NORMAL_VAL(
        "property",
        RellTokenModifier.ENTITY_ATTR_NORMAL_VAL,
        RellTokenModifier.MODIFICATION,
        RellTokenModifier.READONLY
    ),
    ENTITY_ATTR_NORMAL_VAR(
        "property",
        RellTokenModifier.ENTITY_ATTR_NORMAL_VAR,
        RellTokenModifier.MODIFICATION,
        RellTokenModifier.READONLY
    ),
    ENTITY_ATTR_KEYINDEX_VAL(
        "property",
        RellTokenModifier.ENTITY_ATTR_KEYINDEX_VAL,
        RellTokenModifier.MODIFICATION,
        RellTokenModifier.READONLY
    ),
    ENTITY_ATTR_KEYINDEX_VAR(
        "property",
        RellTokenModifier.ENTITY_ATTR_KEYINDEX_VAR,
        RellTokenModifier.MODIFICATION,
        RellTokenModifier.READONLY
    ),
    STRUCT("struct", RellTokenModifier.STRUCT),
    STRUCT_ATTR_VAL("property", RellTokenModifier.STRUCT_ATTR_VAL),
    STRUCT_ATTR_VAR("property", RellTokenModifier.STRUCT_ATTR_VAR),
    TUPLE_ATTR("property", RellTokenModifier.TUPLE_ATTR),
    OPERATION("function", RellTokenModifier.OPERATION),
    QUERY("function", RellTokenModifier.QUERY),
    FUNCTION("function", RellTokenModifier.FUNCTION),
    FUNCTION_EXTENDABLE("function", RellTokenModifier.FUNCTION_EXTENDABLE),
    NAMED_ARGUMENT("variable", RellTokenModifier.NAMED_ARGUMENT),
    LOCAL_VAL("variable", RellTokenModifier.LOCAL_VAL, RellTokenModifier.READONLY),
    LOCAL_VAR("variable", RellTokenModifier.LOCAL_VAR),
    AT_ALIAS("variable", RellTokenModifier.AT_ALIAS),
    LOCAL_PARAMETER("variable", RellTokenModifier.LOCAL_VAL, RellTokenModifier.PARAMETER),
    FUNCTION_CALL("function", RellTokenModifier.FUNCTION, RellTokenModifier.CALL),
    OPERATION_CALL("function", RellTokenModifier.OPERATION, RellTokenModifier.CALL),
    QUERY_CALL("function", RellTokenModifier.QUERY, RellTokenModifier.CALL);

    val modifiersAsList: List<RellTokenModifier> = modifiers.toList()
    val tokenId: Int = this.ordinal
}

fun tokenFromIdeSymbolInfo(info: IdeSymbolInfo): RellTokenType {
    val kind = info.kind
    return when (kind) {
        IdeSymbolKind.DEF_IMPORT_ALIAS -> RellTokenType.DEFAULT
        IdeSymbolKind.DEF_CONSTANT -> RellTokenType.GLOBAL_CONSTANT
        IdeSymbolKind.DEF_ENTITY -> RellTokenType.ENTITY
        IdeSymbolKind.DEF_ENUM -> RellTokenType.ENUM
        IdeSymbolKind.DEF_FUNCTION_ABSTRACT -> RellTokenType.FUNCTION_EXTENDABLE
        IdeSymbolKind.DEF_FUNCTION_EXTEND -> RellTokenType.FUNCTION
        IdeSymbolKind.DEF_FUNCTION_EXTENDABLE -> RellTokenType.FUNCTION_EXTENDABLE
        IdeSymbolKind.DEF_FUNCTION -> getCallOrDefault(info, RellTokenType.FUNCTION_CALL, RellTokenType.FUNCTION)
        IdeSymbolKind.DEF_FUNCTION_SYSTEM -> RellTokenType.FUNCTION_CALL
        IdeSymbolKind.DEF_IMPORT_MODULE -> RellTokenType.MODULE
        IdeSymbolKind.DEF_NAMESPACE -> RellTokenType.NAMESPACE
        IdeSymbolKind.DEF_OBJECT -> RellTokenType.OBJECT
        IdeSymbolKind.DEF_OPERATION -> getCallOrDefault(info, RellTokenType.OPERATION_CALL, RellTokenType.OPERATION)
        IdeSymbolKind.DEF_QUERY -> getCallOrDefault(info, RellTokenType.QUERY_CALL, RellTokenType.QUERY)
        IdeSymbolKind.DEF_STRUCT -> RellTokenType.STRUCT
        IdeSymbolKind.DEF_TYPE -> RellTokenType.TYPE
        IdeSymbolKind.EXPR_CALL_ARG -> RellTokenType.NAMED_ARGUMENT
        IdeSymbolKind.EXPR_IMPORT_ALIAS -> RellTokenType.MODULE
        IdeSymbolKind.LOC_AT_ALIAS -> RellTokenType.AT_ALIAS
        IdeSymbolKind.LOC_PARAMETER -> RellTokenType.LOCAL_PARAMETER
        IdeSymbolKind.LOC_VAL -> RellTokenType.LOCAL_VAL
        IdeSymbolKind.LOC_VAR -> RellTokenType.LOCAL_VAR
        IdeSymbolKind.MEM_ENTITY_ATTR_INDEX -> RellTokenType.ENTITY_ATTR_KEYINDEX_VAL
        IdeSymbolKind.MEM_ENTITY_ATTR_INDEX_VAR -> RellTokenType.ENTITY_ATTR_KEYINDEX_VAR
        IdeSymbolKind.MEM_ENTITY_ATTR_KEY -> RellTokenType.ENTITY_ATTR_KEYINDEX_VAL
        IdeSymbolKind.MEM_ENTITY_ATTR_KEY_VAR -> RellTokenType.ENTITY_ATTR_KEYINDEX_VAR
        IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL -> RellTokenType.ENTITY_ATTR_NORMAL_VAL
        IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL_VAR -> RellTokenType.ENTITY_ATTR_NORMAL_VAR
        IdeSymbolKind.MEM_ENTITY_ATTR_ROWID -> RellTokenType.ENTITY_ATTR_KEYINDEX_VAL
        IdeSymbolKind.MEM_ENUM_VALUE -> RellTokenType.ENUM_VALUE
        IdeSymbolKind.MEM_STRUCT_ATTR -> RellTokenType.STRUCT_ATTR_VAL
        IdeSymbolKind.MEM_STRUCT_ATTR_VAR -> RellTokenType.STRUCT_ATTR_VAR
        IdeSymbolKind.MEM_SYS_PROPERTY -> RellTokenType.DEFAULT
        IdeSymbolKind.MEM_TUPLE_ATTR -> RellTokenType.TUPLE_ATTR
        IdeSymbolKind.MOD_ANNOTATION -> RellTokenType.ANNOTATION
        IdeSymbolKind.MOD_ANNOTATION_LEGACY -> RellTokenType.ANNOTATION
        IdeSymbolKind.UNKNOWN -> RellTokenType.DEFAULT
        IdeSymbolKind.MEM_SYS_PROPERTY_PURE -> RellTokenType.DEFAULT
    }
}

fun getCallOrDefault(info: IdeSymbolInfo, call: RellTokenType, default: RellTokenType): RellTokenType {
    return if (info.defId == null) call else default
}