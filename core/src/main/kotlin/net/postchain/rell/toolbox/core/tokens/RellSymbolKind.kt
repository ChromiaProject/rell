package net.postchain.rell.toolbox.core.tokens

import net.postchain.rell.base.utils.ide.IdeSymbolKind


enum class RellSymbolModifier(
    val modifierStringId: String
) {
    READONLY("readonly"),
    MODIFICATION("modification"),

    //Rell keyword modifiers
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
    AT_ALIAS("rell-at_alias");
}


enum class RellSymbolKind(
    val tokenId: Int,
    val tokenStringId: String,
    vararg val modifiers: RellSymbolModifier
) {
    DEFAULT(
        0,
        "keyword",
        RellSymbolModifier.DEFAULT
    ),
    MODULE(
        1,
        "namespace",
        RellSymbolModifier.MODULE
    ),
    ANNOTATION(
        2,
        "decorator",
        RellSymbolModifier.ANNOTATION
    ),
    NAMESPACE(
        3,
        "namespace",
        RellSymbolModifier.NAMESPACE
    ),
    TYPE(
        4,
        "type",
        RellSymbolModifier.TYPE
    ),
    ENUM(
        5,
        "enum",
        RellSymbolModifier.ENUM
    ),
    ENUM_VALUE(
        6,
        "enumMember",
        RellSymbolModifier.ENUM_VALUE
    ),
    GLOBAL_CONSTANT(
        7,
        "variable",
        RellSymbolModifier.GLOBAL_CONSTANT,
        RellSymbolModifier.READONLY
    ),
    ENTITY(
        8,
        "class",
        RellSymbolModifier.ENTITY,
        RellSymbolModifier.MODIFICATION
    ),
    OBJECT(
        9,
        "class",
        RellSymbolModifier.OBJECT,
        RellSymbolModifier.MODIFICATION
    ),
    ENTITY_ATTR_NORMAL_VAL(
        10,
        "property",
        RellSymbolModifier.ENTITY_ATTR_NORMAL_VAL,
        RellSymbolModifier.MODIFICATION,
        RellSymbolModifier.READONLY
    ),
    ENTITY_ATTR_NORMAL_VAR(
        11,
        "property",
        RellSymbolModifier.ENTITY_ATTR_NORMAL_VAR,
        RellSymbolModifier.MODIFICATION,
        RellSymbolModifier.READONLY
    ),
    ENTITY_ATTR_KEYINDEX_VAL(
        12,
        "property",
        RellSymbolModifier.ENTITY_ATTR_KEYINDEX_VAL,
        RellSymbolModifier.MODIFICATION,
        RellSymbolModifier.READONLY
    ),
    ENTITY_ATTR_KEYINDEX_VAR(
        13,
        "property",
        RellSymbolModifier.ENTITY_ATTR_KEYINDEX_VAR,
        RellSymbolModifier.MODIFICATION,
        RellSymbolModifier.READONLY
    ),
    STRUCT(
        14,
        "struct",
        RellSymbolModifier.STRUCT
    ),
    STRUCT_ATTR_VAL(
        15,
        "property",
        RellSymbolModifier.STRUCT_ATTR_VAL
    ),
    STRUCT_ATTR_VAR(
        16,
        "property",
        RellSymbolModifier.STRUCT_ATTR_VAR
    ),
    TUPLE_ATTR(
        17,
        "property",
        RellSymbolModifier.TUPLE_ATTR
    ),
    OPERATION(
        18,
        "function",
        RellSymbolModifier.OPERATION
    ),
    QUERY(
        19,
        "function",
        RellSymbolModifier.QUERY
    ),
    FUNCTION(
        20,
        "function",
        RellSymbolModifier.FUNCTION
    ),
    FUNCTION_EXTENDABLE(
        21,
        "function",
        RellSymbolModifier.FUNCTION_EXTENDABLE
    ),
    NAMED_ARGUMENT(
        22,
        "variable",
        RellSymbolModifier.NAMED_ARGUMENT
    ),
    LOCAL_VAL(
        23,
        "variable",
        RellSymbolModifier.LOCAL_VAL,
        RellSymbolModifier.READONLY
    ),
    LOCAL_VAR(
        24,
        "variable",
        RellSymbolModifier.LOCAL_VAR
    ),
    AT_ALIAS(
        25,
        "variable",
        RellSymbolModifier.AT_ALIAS
    );

    val modifiersAsList: List<RellSymbolModifier> = modifiers.toList()

    companion object {
        init {
            val numIdList = entries.toTypedArray().map { x: RellSymbolKind -> x.tokenId }
            if (numIdList.min() != 0 ||
                numIdList.size != numIdList.max() + 1 ||
                numIdList.toSet().size != numIdList.size
            ) {
                throw RuntimeException("Inconsistent set of num ID's")
            }
        }

        fun forIdeKind(kind: IdeSymbolKind): RellSymbolKind {
            when (kind) {
                IdeSymbolKind.DEF_IMPORT_ALIAS -> return DEFAULT
                IdeSymbolKind.DEF_CONSTANT -> return GLOBAL_CONSTANT
                IdeSymbolKind.DEF_ENTITY -> return ENTITY
                IdeSymbolKind.DEF_ENUM -> return ENUM
                IdeSymbolKind.DEF_FUNCTION_ABSTRACT -> return FUNCTION_EXTENDABLE
                IdeSymbolKind.DEF_FUNCTION_EXTEND -> return FUNCTION
                IdeSymbolKind.DEF_FUNCTION_EXTENDABLE -> return FUNCTION_EXTENDABLE
                IdeSymbolKind.DEF_FUNCTION -> return FUNCTION
                IdeSymbolKind.DEF_FUNCTION_SYSTEM -> return FUNCTION
                IdeSymbolKind.DEF_IMPORT_MODULE -> return MODULE
                IdeSymbolKind.DEF_NAMESPACE -> return NAMESPACE
                IdeSymbolKind.DEF_OBJECT -> return OBJECT
                IdeSymbolKind.DEF_OPERATION -> return OPERATION
                IdeSymbolKind.DEF_QUERY -> return QUERY
                IdeSymbolKind.DEF_STRUCT -> return STRUCT
                IdeSymbolKind.DEF_TYPE -> return TYPE
                IdeSymbolKind.EXPR_CALL_ARG -> return NAMED_ARGUMENT
                IdeSymbolKind.EXPR_IMPORT_ALIAS -> return MODULE
                IdeSymbolKind.LOC_AT_ALIAS -> return AT_ALIAS
                IdeSymbolKind.LOC_PARAMETER -> return LOCAL_VAL
                IdeSymbolKind.LOC_VAL -> return LOCAL_VAL
                IdeSymbolKind.LOC_VAR -> return LOCAL_VAR
                IdeSymbolKind.MEM_ENTITY_ATTR_INDEX -> return ENTITY_ATTR_KEYINDEX_VAL
                IdeSymbolKind.MEM_ENTITY_ATTR_INDEX_VAR -> return ENTITY_ATTR_KEYINDEX_VAR
                IdeSymbolKind.MEM_ENTITY_ATTR_KEY -> return ENTITY_ATTR_KEYINDEX_VAL
                IdeSymbolKind.MEM_ENTITY_ATTR_KEY_VAR -> return ENTITY_ATTR_KEYINDEX_VAR
                IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL -> return ENTITY_ATTR_NORMAL_VAL
                IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL_VAR -> return ENTITY_ATTR_NORMAL_VAR
                IdeSymbolKind.MEM_ENTITY_ATTR_ROWID -> return ENTITY_ATTR_KEYINDEX_VAL
                IdeSymbolKind.MEM_ENUM_VALUE -> return ENUM_VALUE
                IdeSymbolKind.MEM_STRUCT_ATTR -> return STRUCT_ATTR_VAL
                IdeSymbolKind.MEM_STRUCT_ATTR_VAR -> return STRUCT_ATTR_VAR
                IdeSymbolKind.MEM_SYS_PROPERTY -> return DEFAULT
                IdeSymbolKind.MEM_TUPLE_ATTR -> return TUPLE_ATTR
                IdeSymbolKind.MOD_ANNOTATION -> return ANNOTATION
                IdeSymbolKind.MOD_ANNOTATION_LEGACY -> return ANNOTATION
                IdeSymbolKind.UNKNOWN -> return DEFAULT
                IdeSymbolKind.MEM_SYS_PROPERTY_PURE -> return DEFAULT
            }
        }
    }
}



