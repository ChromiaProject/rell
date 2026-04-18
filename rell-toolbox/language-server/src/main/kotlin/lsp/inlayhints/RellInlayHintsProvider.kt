/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.inlayhints

import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.inlayhints.visitor.ParameterHintsVisitor
import net.postchain.rell.toolbox.lsp.inlayhints.visitor.ReturnTypeHintsVisitor
import net.postchain.rell.toolbox.lsp.inlayhints.visitor.TypeInferenceVisitor
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either

class RellInlayHintsProvider {

    fun provideInlayHints(resource: Resource, range: Range, config: RellInlayHintsConfig): List<InlayHint> =
        listOfNotNull(
            if (config.isVariableTypesEnabled) processTypeInferenceHints(resource, range) else null,
            if (config.isParameterNamesEnabled) processParamHints(resource, range) else null,
            if (config.isReturnTypesEnabled) processReturnTypeHints(resource, range) else null
        ).flatten()

    private fun processTypeInferenceHints(resource: Resource, range: Range) = buildList {
        val visitor = TypeInferenceVisitor(resource, range, this)
        visitor.visit(resource.parseTree)
    }

    private fun processParamHints(resource: Resource, range: Range) = buildList {
        val visitor = ParameterHintsVisitor(resource, range, this)
        visitor.visit(resource.parseTree)
    }

    private fun processReturnTypeHints(resource: Resource, range: Range) =
        buildList {
            val visitor = ReturnTypeHintsVisitor(resource, range, this)
            visitor.visit(resource.parseTree)
        }

    companion object {
        fun isInRange(pos: Position, range: Range): Boolean =
            pos.line in range.start.line..range.end.line &&
                (pos.line > range.start.line || pos.character >= range.start.character) &&
                (pos.line < range.end.line || pos.character <= range.end.character)

        fun createTypeInlayHint(
            position: Position,
            type: String,
            kind: InlayHintKind = InlayHintKind.Type
        ): InlayHint =
            InlayHint().apply {
                this.position = position
                this.label = Either.forLeft(": $type")
                this.kind = kind
                this.paddingLeft = true
                this.paddingRight = false
                // NOTE: for now show type in tooltip also, in case it is too long and doesn't fit inside the editor
                this.tooltip = Either.forLeft(type)
            }

        fun createParameterInlayHint(
            position: Position,
            parameterName: String
        ): InlayHint =
            InlayHint().apply {
                this.position = position
                this.label = Either.forLeft("$parameterName=")
                this.kind = InlayHintKind.Parameter
                this.paddingLeft = false
                this.paddingRight = true
                this.tooltip = Either.forLeft(parameterName)
            }
    }
}
