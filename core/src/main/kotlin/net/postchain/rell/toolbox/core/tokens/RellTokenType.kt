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
    AT_ALIAS("variable", RellTokenModifier.AT_ALIAS);

    val modifiersAsList: List<RellTokenModifier> = modifiers.toList()
}
