package net.postchain.rell.toolbox.core.tokens

import net.postchain.rell.base.utils.ide.IdeSymbolKind


enum class RellSymbolModifier(
    val modifierStringId: String
) {
    READONLY("readonly"),
    MODIFICATION("modification");
}


enum class RellSymbolKind(
    val tokenId: Int,
    val tokenStringId: String,
    val modifiers: List<RellSymbolModifier> = listOf()
) {
    DEFAULT(0, "keyword"),
    MODULE(1, "namespace"),
    ANNOTATION(2, "decorator"),
    NAMESPACE(3, "namespace"),
    TYPE(4, "type"),
    ENUM(5, "enum"),
    ENUM_VALUE(6, "enumMember"),
    GLOBAL_CONSTANT(7, "variable", listOf(RellSymbolModifier.READONLY)),
    ENTITY(8, "class", listOf(RellSymbolModifier.MODIFICATION)),
    OBJECT(9, "class", listOf(RellSymbolModifier.MODIFICATION)),
    ENTITY_ATTR_NORMAL_VAL(10, "property", listOf(RellSymbolModifier.MODIFICATION, RellSymbolModifier.READONLY)),
    ENTITY_ATTR_NORMAL_VAR(11, "property", listOf(RellSymbolModifier.MODIFICATION, RellSymbolModifier.READONLY)),
    ENTITY_ATTR_KEYINDEX_VAL(12, "property", listOf(RellSymbolModifier.MODIFICATION, RellSymbolModifier.READONLY)),
    ENTITY_ATTR_KEYINDEX_VAR(13, "property", listOf(RellSymbolModifier.MODIFICATION, RellSymbolModifier.READONLY)),
    STRUCT(14, "struct"),
    STRUCT_ATTR_VAL(15, "property"),
    STRUCT_ATTR_VAR(16, "property"),
    TUPLE_ATTR(17, "property"),
    OPERATION(18, "function"),
    QUERY(19, "function"),
    FUNCTION(20, "function"),
    FUNCTION_EXTENDABLE(21, "function"),
    NAMED_ARGUMENT(22, "variable"),
    LOCAL_VAL(23, "variable", listOf(RellSymbolModifier.READONLY)),
    LOCAL_VAR(24, "variable"),
    AT_ALIAS(25, "variable");

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



