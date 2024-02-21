package net.postchain.rell.toolbox.core.tokens

import net.postchain.rell.base.utils.ide.IdeSymbolKind
import org.antlr.v4.runtime.tree.TerminalNode

class Token(symbKind: IdeSymbolKind, node: TerminalNode) : Comparable<Token> {
    val line = node.symbol.line - 1
    val col = node.symbol.charPositionInLine
    val len = node.symbol.text.length
    val tokenType = tokenFromIdeKind(symbKind)

    override operator fun compareTo(other: Token): Int {
        var diff = line.compareTo(other.line)
        if (diff == 0) diff = col.compareTo(other.col)
        return diff
    }

    private fun tokenFromIdeKind(kind: IdeSymbolKind): RellTokenType {
        when (kind) {
            IdeSymbolKind.DEF_IMPORT_ALIAS -> return RellTokenType.DEFAULT
            IdeSymbolKind.DEF_CONSTANT -> return RellTokenType.GLOBAL_CONSTANT
            IdeSymbolKind.DEF_ENTITY -> return RellTokenType.ENTITY
            IdeSymbolKind.DEF_ENUM -> return RellTokenType.ENUM
            IdeSymbolKind.DEF_FUNCTION_ABSTRACT -> return RellTokenType.FUNCTION_EXTENDABLE
            IdeSymbolKind.DEF_FUNCTION_EXTEND -> return RellTokenType.FUNCTION
            IdeSymbolKind.DEF_FUNCTION_EXTENDABLE -> return RellTokenType.FUNCTION_EXTENDABLE
            IdeSymbolKind.DEF_FUNCTION -> return RellTokenType.FUNCTION
            IdeSymbolKind.DEF_FUNCTION_SYSTEM -> return RellTokenType.FUNCTION
            IdeSymbolKind.DEF_IMPORT_MODULE -> return RellTokenType.MODULE
            IdeSymbolKind.DEF_NAMESPACE -> return RellTokenType.NAMESPACE
            IdeSymbolKind.DEF_OBJECT -> return RellTokenType.OBJECT
            IdeSymbolKind.DEF_OPERATION -> return RellTokenType.OPERATION
            IdeSymbolKind.DEF_QUERY -> return RellTokenType.QUERY
            IdeSymbolKind.DEF_STRUCT -> return RellTokenType.STRUCT
            IdeSymbolKind.DEF_TYPE -> return RellTokenType.TYPE
            IdeSymbolKind.EXPR_CALL_ARG -> return RellTokenType.NAMED_ARGUMENT
            IdeSymbolKind.EXPR_IMPORT_ALIAS -> return RellTokenType.MODULE
            IdeSymbolKind.LOC_AT_ALIAS -> return RellTokenType.AT_ALIAS
            IdeSymbolKind.LOC_PARAMETER -> return RellTokenType.LOCAL_VAL
            IdeSymbolKind.LOC_VAL -> return RellTokenType.LOCAL_VAL
            IdeSymbolKind.LOC_VAR -> return RellTokenType.LOCAL_VAR
            IdeSymbolKind.MEM_ENTITY_ATTR_INDEX -> return RellTokenType.ENTITY_ATTR_KEYINDEX_VAL
            IdeSymbolKind.MEM_ENTITY_ATTR_INDEX_VAR -> return RellTokenType.ENTITY_ATTR_KEYINDEX_VAR
            IdeSymbolKind.MEM_ENTITY_ATTR_KEY -> return RellTokenType.ENTITY_ATTR_KEYINDEX_VAL
            IdeSymbolKind.MEM_ENTITY_ATTR_KEY_VAR -> return RellTokenType.ENTITY_ATTR_KEYINDEX_VAR
            IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL -> return RellTokenType.ENTITY_ATTR_NORMAL_VAL
            IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL_VAR -> return RellTokenType.ENTITY_ATTR_NORMAL_VAR
            IdeSymbolKind.MEM_ENTITY_ATTR_ROWID -> return RellTokenType.ENTITY_ATTR_KEYINDEX_VAL
            IdeSymbolKind.MEM_ENUM_VALUE -> return RellTokenType.ENUM_VALUE
            IdeSymbolKind.MEM_STRUCT_ATTR -> return RellTokenType.STRUCT_ATTR_VAL
            IdeSymbolKind.MEM_STRUCT_ATTR_VAR -> return RellTokenType.STRUCT_ATTR_VAR
            IdeSymbolKind.MEM_SYS_PROPERTY -> return RellTokenType.DEFAULT
            IdeSymbolKind.MEM_TUPLE_ATTR -> return RellTokenType.TUPLE_ATTR
            IdeSymbolKind.MOD_ANNOTATION -> return RellTokenType.ANNOTATION
            IdeSymbolKind.MOD_ANNOTATION_LEGACY -> return RellTokenType.ANNOTATION
            IdeSymbolKind.UNKNOWN -> return RellTokenType.DEFAULT
            IdeSymbolKind.MEM_SYS_PROPERTY_PURE -> return RellTokenType.DEFAULT
        }
    }
}
