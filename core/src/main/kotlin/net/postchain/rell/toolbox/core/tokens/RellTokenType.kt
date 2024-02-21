package net.postchain.rell.toolbox.core.tokens


enum class RellTokenModifier(
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


enum class RellTokenType(
    val tokenId: Int,
    val tokenStringId: String,
    vararg val modifiers: RellTokenModifier
) {
    DEFAULT(
        0,
        "keyword",
        RellTokenModifier.DEFAULT
    ),
    MODULE(
        1,
        "namespace",
        RellTokenModifier.MODULE
    ),
    ANNOTATION(
        2,
        "decorator",
        RellTokenModifier.ANNOTATION
    ),
    NAMESPACE(
        3,
        "namespace",
        RellTokenModifier.NAMESPACE
    ),
    TYPE(
        4,
        "type",
        RellTokenModifier.TYPE
    ),
    ENUM(
        5,
        "enum",
        RellTokenModifier.ENUM
    ),
    ENUM_VALUE(
        6,
        "enumMember",
        RellTokenModifier.ENUM_VALUE
    ),
    GLOBAL_CONSTANT(
        7,
        "variable",
        RellTokenModifier.GLOBAL_CONSTANT,
        RellTokenModifier.READONLY
    ),
    ENTITY(
        8,
        "class",
        RellTokenModifier.ENTITY,
        RellTokenModifier.MODIFICATION
    ),
    OBJECT(
        9,
        "class",
        RellTokenModifier.OBJECT,
        RellTokenModifier.MODIFICATION
    ),
    ENTITY_ATTR_NORMAL_VAL(
        10,
        "property",
        RellTokenModifier.ENTITY_ATTR_NORMAL_VAL,
        RellTokenModifier.MODIFICATION,
        RellTokenModifier.READONLY
    ),
    ENTITY_ATTR_NORMAL_VAR(
        11,
        "property",
        RellTokenModifier.ENTITY_ATTR_NORMAL_VAR,
        RellTokenModifier.MODIFICATION,
        RellTokenModifier.READONLY
    ),
    ENTITY_ATTR_KEYINDEX_VAL(
        12,
        "property",
        RellTokenModifier.ENTITY_ATTR_KEYINDEX_VAL,
        RellTokenModifier.MODIFICATION,
        RellTokenModifier.READONLY
    ),
    ENTITY_ATTR_KEYINDEX_VAR(
        13,
        "property",
        RellTokenModifier.ENTITY_ATTR_KEYINDEX_VAR,
        RellTokenModifier.MODIFICATION,
        RellTokenModifier.READONLY
    ),
    STRUCT(
        14,
        "struct",
        RellTokenModifier.STRUCT
    ),
    STRUCT_ATTR_VAL(
        15,
        "property",
        RellTokenModifier.STRUCT_ATTR_VAL
    ),
    STRUCT_ATTR_VAR(
        16,
        "property",
        RellTokenModifier.STRUCT_ATTR_VAR
    ),
    TUPLE_ATTR(
        17,
        "property",
        RellTokenModifier.TUPLE_ATTR
    ),
    OPERATION(
        18,
        "function",
        RellTokenModifier.OPERATION
    ),
    QUERY(
        19,
        "function",
        RellTokenModifier.QUERY
    ),
    FUNCTION(
        20,
        "function",
        RellTokenModifier.FUNCTION
    ),
    FUNCTION_EXTENDABLE(
        21,
        "function",
        RellTokenModifier.FUNCTION_EXTENDABLE
    ),
    NAMED_ARGUMENT(
        22,
        "variable",
        RellTokenModifier.NAMED_ARGUMENT
    ),
    LOCAL_VAL(
        23,
        "variable",
        RellTokenModifier.LOCAL_VAL,
        RellTokenModifier.READONLY
    ),
    LOCAL_VAR(
        24,
        "variable",
        RellTokenModifier.LOCAL_VAR
    ),
    AT_ALIAS(
        25,
        "variable",
        RellTokenModifier.AT_ALIAS
    );

    val modifiersAsList: List<RellTokenModifier> = modifiers.toList()

    companion object {
        init {
            val numIdList = entries.toTypedArray().map { x: RellTokenType -> x.tokenId }
            if (numIdList.min() != 0 ||
                numIdList.size != numIdList.max() + 1 ||
                numIdList.toSet().size != numIdList.size
            ) {
                throw RuntimeException("Inconsistent set of num ID's")
            }
        }
    }
}
