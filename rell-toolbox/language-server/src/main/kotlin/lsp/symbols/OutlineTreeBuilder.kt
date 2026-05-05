/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.symbols

import net.postchain.rell.base.compiler.ast.S_Name
import net.postchain.rell.base.compiler.ast.S_Node
import net.postchain.rell.base.utils.ide.IdeOutlineNodeType
import net.postchain.rell.base.utils.ide.IdeOutlineTreeBuilder
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.compiler.parser.antlr.AntlrRellNodeAttachment
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import org.antlr.v4.runtime.ParserRuleContext
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
        val annotations = getAnnotations(node)

        val subInfo = NodeInfo(nameStr, nameRegion, fullRegion, symbolKind, annotations)
        return node(subInfo, type)
    }

    fun getAnnotations(node: S_Node): List<String>? {
        val attachment = node.attachment as AntlrRellNodeAttachment
        var antlrNode: ParserRuleContext? = attachment.node
        // The S_Node attachment for a function/operation/etc. is the inner def context
        // (e.g. FunctionDefContext). Annotations live on the enclosing AnnotatedDefContext.
        // Walk up until we find one — works whether the attachment points to the inner def
        // or directly at the AnnotatedDefContext.
        while (antlrNode != null && antlrNode !is RellParser.AnnotatedDefContext) {
            antlrNode = antlrNode.parent as? ParserRuleContext
        }
        if (antlrNode !is RellParser.AnnotatedDefContext) return null
        val modifiers = antlrNode.modifiers() ?: return emptyList()
        return modifiers.modifier().mapNotNull { mod ->
            // `modifier` is `'abstract' | 'mutable' | 'override' | annotation`. We only care
            // about `annotation` (e.g. `@test`, `@disabled`) — return its identifier text.
            mod.annotation()?.RULE_ID()?.text
        }
    }
}

class NodeInfo(
    val text: String,
    val nameRegion: Range?,
    val fullRegion: Range?,
    val symbolKind: SymbolKind,
    val annotations: List<String>? = null
) {
    fun hasAnnotation(annotation: String): Boolean {
        return this.annotations?.contains(annotation) ?: false
    }
}

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

    fun getChildren(): List<OutlineNode> = children
    fun getInfo(): NodeInfo = info
}

fun getFullRegion(node: S_Node, name: S_Node): Range {
    val attachment = node.attachment as AntlrRellNodeAttachment
    val nameAttachment = name.attachment as AntlrRellNodeAttachment
    // For annotated definitions (functions, operations, etc.), the visible "full region" must
    // include the leading modifiers/annotations. The inner def context starts after the
    // annotations, so walk up to the enclosing AnnotatedDefContext only when (a) the parent's
    // start token is strictly earlier (i.e. there are actually modifiers present) AND (b) the
    // parent's stop is on the same line as the inner def's stop (so we don't inflate symbol
    // ranges across error-recovered regions for incomplete code).
    val regionNode: ParserRuleContext = run {
        val inner = attachment.node
        var p: ParserRuleContext? = inner
        while (p?.parent is ParserRuleContext) {
            val parent = p.parent as ParserRuleContext
            if (parent is RellParser.AnnotatedDefContext) {
                val widen = parent.start != null && inner.start != null
                    && parent.start.startIndex < inner.start.startIndex
                    && parent.stop != null && inner.stop != null
                    && parent.stop.stopIndex == inner.stop.stopIndex
                return@run if (widen) parent else inner
            }
            p = parent
        }
        inner
    }
    val startPos = Position(regionNode.start.line - 1, regionNode.start.charPositionInLine)
    return if (regionNode.stop.stopIndex > nameAttachment.node.stop.stopIndex) {
        Range(
            startPos,
            Position(regionNode.stop.line - 1, regionNode.stop.charPositionInLine)
        )
    } else {
        val nodeLength = regionNode.text.length
        Range(
            startPos,
            Position(nameAttachment.node.stop.line - 1, nameAttachment.node.stop.charPositionInLine + nodeLength)
        )
    }
}

fun getNameRegion(node: S_Node): Range {
    val attachment = node.attachment as AntlrRellNodeAttachment
    val nodeLength = attachment.node.text.length
    val startPos = Position(attachment.node.start.line - 1, attachment.node.start.charPositionInLine)
    val endPos = if (attachment.node.start.line == attachment.node.stop.line &&
        attachment.node.start.charPositionInLine == attachment.node.stop.charPositionInLine
    ) {
        Position(attachment.node.stop.line - 1, attachment.node.stop.charPositionInLine + nodeLength)
    } else {
        Position(attachment.node.stop.line - 1, attachment.node.stop.charPositionInLine)
    }

    return Range(startPos, endPos)
}

fun getSymbolKind(type: IdeOutlineNodeType): SymbolKind = when (type) {
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
}
