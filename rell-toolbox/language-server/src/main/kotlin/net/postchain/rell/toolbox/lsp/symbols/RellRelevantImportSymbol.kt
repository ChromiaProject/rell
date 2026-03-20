package net.postchain.rell.toolbox.lsp.symbols

import net.postchain.rell.base.utils.ide.IdeSymbolKind

enum class RellRelevantImportSymbol(
    val ideSymbolKind: IdeSymbolKind,
    val displayName: String
) {
    CONSTANT(IdeSymbolKind.DEF_CONSTANT, "constant"),
    ENTITY(IdeSymbolKind.DEF_ENTITY, "entity"),
    ENUM(IdeSymbolKind.DEF_ENUM, "enum"),
    FUNCTION(IdeSymbolKind.DEF_FUNCTION, "function"),
    NAMESPACE(IdeSymbolKind.DEF_NAMESPACE, "namespace"),
    OBJECT(IdeSymbolKind.DEF_OBJECT, "object"),
    OPERATION(IdeSymbolKind.DEF_OPERATION, "operation"),
    QUERY(IdeSymbolKind.DEF_QUERY, "query"),
    STRUCT(IdeSymbolKind.DEF_STRUCT, "struct"),
    FUNCTION_EXTEND(IdeSymbolKind.DEF_FUNCTION_EXTEND, "function"),
    FUNCTION_EXTENDABLE(IdeSymbolKind.DEF_FUNCTION_EXTENDABLE, "function");

    companion object {
        private val ideSymbolKindMap = entries.associateBy { it.ideSymbolKind }

        fun fromIdeSymbolKind(kind: IdeSymbolKind): RellRelevantImportSymbol? = ideSymbolKindMap[kind]
        
        fun getAllIdeSymbolKinds(): List<IdeSymbolKind> = entries.map { it.ideSymbolKind }
    }
}