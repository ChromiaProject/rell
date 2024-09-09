package net.postchain.rell.toolbox.lsp.symbols

import net.postchain.rell.base.compiler.ast.S_Name
import net.postchain.rell.base.compiler.ast.S_Node
import net.postchain.rell.base.utils.ide.IdeOutlineNodeType
import net.postchain.rell.base.utils.ide.IdeOutlineTreeBuilder
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.toolbox.transformer.AntlrRellNodeAttachment
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind

class OutlineTreeBuilder(
    private val nodeInfo: NodeInfo,
    private val type: IdeOutlineNodeType?,
) : IdeOutlineTreeBuilder {
    private val children = mutableListOf<OutlineTreeBuilder>()

    fun build(): OutlineNode {
        return OutlineNode(this, nodeInfo)
    }

    fun buildChildren(): List<OutlineNode> {
        val attributes = children.filter { b: OutlineTreeBuilder -> b.type == IdeOutlineNodeType.ATTRIBUTE }
            .map { b: OutlineTreeBuilder -> b.nodeInfo.text }.toSet()

        val result = mutableListOf<OutlineNode>()
        for (child in children) {
            if (child.type == IdeOutlineNodeType.KEY_INDEX && attributes.contains(child.nodeInfo.text)) {
                continue
            }
            val node = child.build()
            result.add(node)
        }
        return result
    }

    private fun node(newNodeInfo: NodeInfo, type: IdeOutlineNodeType): OutlineTreeBuilder {
        val subB = OutlineTreeBuilder(newNodeInfo, type)
        children.add(subB)
        return subB
    }

    override fun node(node: S_Node, name: S_Name, type: IdeOutlineNodeType): IdeOutlineTreeBuilder {
        val nameStr = name.toString()
        val fullRegion = getFullRegion(node, name)
        val nameRegion = getNameRegion(name)
        val symbolKind = getSymbolKind(type)
        val subInfo = NodeInfo(nameStr, nameRegion, fullRegion, symbolKind)
        return node(subInfo, type)
    }

    private fun getFullRegion(node: S_Node, name: S_Node): Range {
        val attachment = node.attachment as AntlrRellNodeAttachment
        val nameAttachment = name.attachment as AntlrRellNodeAttachment
        val startPos = Position(attachment.node.start.line - 1, attachment.node.start.charPositionInLine)
        return if (attachment.node.stop.stopIndex > nameAttachment.node.stop.stopIndex) {
            Range(
                startPos,
                Position(attachment.node.stop.line - 1, attachment.node.stop.charPositionInLine)
            )
        } else {
            val nodeLength = attachment.node.text.length
            Range(
                startPos,
                Position(nameAttachment.node.stop.line - 1, nameAttachment.node.stop.charPositionInLine + nodeLength)
            )
        }
    }

    private fun getNameRegion(node: S_Node): Range {
        val attachment = node.attachment as AntlrRellNodeAttachment
        val nodeLength = attachment.node.text.length
        return Range(
            Position(attachment.node.start.line - 1, attachment.node.start.charPositionInLine),
            Position(attachment.node.stop.line - 1, attachment.node.stop.charPositionInLine + nodeLength)
        )
    }

    private fun getSymbolKind(type: IdeOutlineNodeType): SymbolKind {
        return when (type) {
            IdeOutlineNodeType.ENTITY -> SymbolKind.Class
            IdeOutlineNodeType.OBJECT -> SymbolKind.Object
            IdeOutlineNodeType.STRUCT -> SymbolKind.Struct
            IdeOutlineNodeType.ATTRIBUTE -> SymbolKind.Property
            IdeOutlineNodeType.KEY_INDEX -> SymbolKind.Property
            IdeOutlineNodeType.ENUM -> SymbolKind.Enum
            IdeOutlineNodeType.ENUM_ATTRIBUTE -> SymbolKind.EnumMember
            IdeOutlineNodeType.NAMESPACE -> SymbolKind.Namespace
            IdeOutlineNodeType.FUNCTION -> SymbolKind.Function
            IdeOutlineNodeType.OPERATION -> SymbolKind.Method
            IdeOutlineNodeType.QUERY -> SymbolKind.Function
            IdeOutlineNodeType.IMPORT -> SymbolKind.Package
            IdeOutlineNodeType.CONSTANT -> SymbolKind.Constant
            else -> throw IllegalArgumentException("Unknown symbol kind: $type")
        }
    }
}

class NodeInfo(val text: String, val nameRegion: Range?, val fullRegion: Range?, val symbolKind: SymbolKind)

class OutlineNode(
    builder: OutlineTreeBuilder,
    private val info: NodeInfo,
) {
    private val children = builder.buildChildren().toImmList()

    fun toDocumentSymbol(): DocumentSymbol {
        return DocumentSymbol(
            info.text,
            info.symbolKind,
            info.fullRegion,
            info.nameRegion,
            "",
            children.map { it.toDocumentSymbol() }.toImmList()
        )
    }
}
