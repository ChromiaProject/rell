package net.postchain.rell.toolbox.core.tokens

import net.postchain.rell.base.utils.ide.IdeSymbolKind
import org.antlr.v4.runtime.tree.TerminalNode

class Token(symbolKind: IdeSymbolKind, node: TerminalNode) : Comparable<Token> {
    val line = node.symbol.line - 1
    val col = node.symbol.charPositionInLine
    val len = node.symbol.text.length
    val tokenType = tokenFromIdeKind(symbolKind)

    override operator fun compareTo(other: Token): Int {
        var diff = line.compareTo(other.line)
        if (diff == 0) diff = col.compareTo(other.col)
        return diff
    }

    private fun tokenFromIdeKind(kind: IdeSymbolKind): RellTokenType {
        return when (kind) {
            IdeSymbolKind.DEF_IMPORT_ALIAS -> RellTokenType.DEFAULT
            IdeSymbolKind.DEF_CONSTANT -> RellTokenType.GLOBAL_CONSTANT
            IdeSymbolKind.DEF_ENTITY -> RellTokenType.ENTITY
            IdeSymbolKind.DEF_ENUM -> RellTokenType.ENUM
            IdeSymbolKind.DEF_FUNCTION_ABSTRACT -> RellTokenType.FUNCTION_EXTENDABLE
            IdeSymbolKind.DEF_FUNCTION_EXTEND -> RellTokenType.FUNCTION
            IdeSymbolKind.DEF_FUNCTION_EXTENDABLE -> RellTokenType.FUNCTION_EXTENDABLE
            IdeSymbolKind.DEF_FUNCTION -> RellTokenType.FUNCTION
            IdeSymbolKind.DEF_FUNCTION_SYSTEM -> RellTokenType.FUNCTION
            IdeSymbolKind.DEF_IMPORT_MODULE -> RellTokenType.MODULE
            IdeSymbolKind.DEF_NAMESPACE -> RellTokenType.NAMESPACE
            IdeSymbolKind.DEF_OBJECT -> RellTokenType.OBJECT
            IdeSymbolKind.DEF_OPERATION -> RellTokenType.OPERATION
            IdeSymbolKind.DEF_QUERY -> RellTokenType.QUERY
            IdeSymbolKind.DEF_STRUCT -> RellTokenType.STRUCT
            IdeSymbolKind.DEF_TYPE -> RellTokenType.TYPE
            IdeSymbolKind.EXPR_CALL_ARG -> RellTokenType.NAMED_ARGUMENT
            IdeSymbolKind.EXPR_IMPORT_ALIAS -> RellTokenType.MODULE
            IdeSymbolKind.LOC_AT_ALIAS -> RellTokenType.AT_ALIAS
            IdeSymbolKind.LOC_PARAMETER -> RellTokenType.LOCAL_VAL
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
}
