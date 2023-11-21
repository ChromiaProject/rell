package net.postchain.rell.toolbox.core.tokens

import net.postchain.rell.base.utils.ide.IdeSymbolKind

const val LSP_ID_PREFIX = "rell-"

enum class RellSymbolKind(
    val numId: Int,
    stringId: String,
    val title: String,
    val colorStr: String,
    vararg styleValues: Style
) {
    DEFAULT(0, "default", "Unclassified name", "#000000"),
    MODULE(1, "module", "Module", "#5453a6", Style.BOLD),
    ANNOTATION(2, "annotation", "Annotation", "#C04000"),
    NAMESPACE(3, "namespace", "Namespace", "#000000"),
    TYPE(4, "type", "Type", "#000000", Style.BOLD),
    ENUM(5, "enum", "Enum", "#000000", Style.BOLD),
    ENUM_VALUE(6, "enum_value", "Enum value", "#0000ff", Style.ITALIC, Style.BOLD),
    GLOBAL_CONSTANT(7, "global_constant", "Global constant", "#0000ff", Style.BOLD, Style.ITALIC),
    ENTITY(8, "entity", "Entity", "#007c7c", Style.BOLD),
    OBJECT(9, "object", "Object", "#007c7c", Style.BOLD),
    ENTITY_ATTR_NORMAL_VAL(10, "entity_attr_normal_val", "Entity attribute", "#007c7c"),
    ENTITY_ATTR_NORMAL_VAR(11, "entity_attr_normal_var", "Entity attribute (mutable)", "#007c7c", Style.UNDERLINE),
    ENTITY_ATTR_KEYINDEX_VAL(12, "entity_attr_keyindex_val", "Entity key/index attribute", "#007c7c", Style.ITALIC),
    ENTITY_ATTR_KEYINDEX_VAR(
        13,
        "entity_attr_keyindex_var",
        "Entity key/index attribute (mutable)",
        "#007c7c",
        Style.ITALIC,
        Style.UNDERLINE
    ),
    STRUCT(14, "struct", "Struct", "#000000", Style.BOLD),
    STRUCT_ATTR_VAL(15, "struct_attr_val", "Struct attribute", "#0000c0"),
    STRUCT_ATTR_VAR(16, "struct_attr_var", "Struct attribute (mutable)", "#0000c0", Style.UNDERLINE),
    TUPLE_ATTR(17, "tuple_attr", "Tuple attribute", "#0000c0"),
    OPERATION(18, "operation", "Operation", "#000000"),
    QUERY(19, "query", "Query", "#000000"),
    FUNCTION(20, "function", "Function", "#000000"),
    FUNCTION_EXTENDABLE(21, "extendable_function", "Function (extendable)", "#000000", Style.ITALIC),
    NAMED_ARGUMENT(22, "named_argument", "Named call argument", "#6a3e3e", Style.ITALIC),
    LOCAL_VAL(23, "local_val", "Local variable (val)", "#6a3e3e"),
    LOCAL_VAR(24, "local_var", "Local variable (var)", "#6a3e3e", Style.UNDERLINE),
    AT_ALIAS(25, "at_alias", "At-expression item alias", "#6a3e3e", Style.ITALIC);

    val lspId: String
    val styleValues: Set<Style>

    init {
        lspId = LSP_ID_PREFIX + stringId
        this.styleValues = setOf(*styleValues)
    }

    enum class Style {
        BOLD,
        ITALIC,
        UNDERLINE
    }

    companion object {
        init {
            val numIdList = entries.toTypedArray().map { x: RellSymbolKind -> x.numId }
            val lspIdList = entries.toTypedArray().map { x: RellSymbolKind -> x.lspId }
            if (numIdList.min() != 0 ||
                numIdList.size != numIdList.max() + 1 ||
                numIdList.toSet().size != numIdList.size) {
                throw RuntimeException("Inconsistent set of num ID's")
            }
            if (lspIdList.toSet().size != lspIdList.size) {
                throw RuntimeException("Not a unique set of lsp ID's")
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
            }
        }
    }
}



