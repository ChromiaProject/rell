package net.postchain.rell.toolbox.lsp.symbols

import net.postchain.rell.base.compiler.ast.S_Name
import net.postchain.rell.base.compiler.ast.S_Node
import net.postchain.rell.base.utils.ide.IdeOutlineNodeType
import net.postchain.rell.base.utils.ide.IdeOutlineTreeBuilder
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI

class WorkspaceSymbolsBuilder(
    val fileUri: URI,
    val filterPredicate: (WorkspaceSymbol) -> Boolean
) : IdeOutlineTreeBuilder {
    private val symbols = mutableListOf<WorkspaceSymbol>()

    override fun node(node: S_Node, name: S_Name, type: IdeOutlineNodeType): IdeOutlineTreeBuilder {
        val nameStr = name.toString()
        val nameRegion = getNameRegion(name)
        val symbolKind = getSymbolKind(type)

        val symbol = WorkspaceSymbol(nameStr, symbolKind, Either.forLeft(Location(fileUri.toString(), nameRegion)))
        if (filterPredicate(symbol) && type != IdeOutlineNodeType.IMPORT) {
            symbols.add(symbol)
        }
        return this
    }

    fun build(): List<WorkspaceSymbol> {
        return symbols
    }
}
