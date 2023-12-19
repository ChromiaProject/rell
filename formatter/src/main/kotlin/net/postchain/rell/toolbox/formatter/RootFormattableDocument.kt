package net.postchain.rell.toolbox.formatter

import net.postchain.rell.toolbox.core.parser.RellLexer
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode

class RootFormattableDocument(val formatter: RellFormatter) : FormattableDocument {
    private val changes = mutableListOf<Changes>()

    override fun append(appendAfterNode: ParserRuleContext?, changeModifier: (Changes) -> Unit) {
        if (appendAfterNode == null) return
        val change = Changes(appendAfterNode.stop.stopIndex + 1, appendAfterNode.stop.stopIndex + 1)
        changeModifier(change)
        hiddenRegionChangeAppendModifier(change, appendAfterNode.stop)
        changes.add(change)
    }

    override fun append(appendAfterNode: TerminalNode?, changeModifier: (Changes) -> Unit) {
        if (appendAfterNode == null) return
        val change = Changes(appendAfterNode.symbol.stopIndex + 1, appendAfterNode.symbol.stopIndex + 1)
        changeModifier(change)
        hiddenRegionChangeAppendModifier(change, appendAfterNode.symbol)
        changes.add(change)
    }


    override fun prepend(prependBeforeNode: ParserRuleContext?, changeModifier: (Changes) -> Unit) {
        if (prependBeforeNode == null) return
        val change = Changes(prependBeforeNode.start.startIndex, prependBeforeNode.start.startIndex)
        changeModifier(change)
        hiddenRegionChangePrependModifier(change, prependBeforeNode.start)
        changes.add(change)
    }

    override fun prepend(prependBeforeNode: TerminalNode?, changeModifier: (Changes) -> Unit) {
        if (prependBeforeNode == null) return
        val change = Changes(prependBeforeNode.symbol.startIndex, prependBeforeNode.symbol.startIndex)
        changeModifier(change)
        hiddenRegionChangePrependModifier(change, prependBeforeNode.symbol)
        changes.add(change)
    }

    override fun surround(surroundNode: ParserRuleContext?, changeModifier: (Changes) -> Unit) {
        if (surroundNode == null) return
        prepend(surroundNode, changeModifier)
        append(surroundNode, changeModifier)
    }

    override fun surround(surroundNode: TerminalNode?, changeModifier: (Changes) -> Unit) {
        prepend(surroundNode, changeModifier)
        append(surroundNode, changeModifier)
    }

    override fun format(node: ParserRuleContext?): ParserRuleContext? {
        if (node == null) return null
        formatter.format(node, this)
        return node
    }

    override fun interiorIndent(interiorNode: ParserRuleContext?) {
        if (interiorNode == null) return
        val nodeStartToken = interiorNode.start
        val nodeStopToken = interiorNode.stop

        var interiorStartToken = formatter.nextSemanticRegion(nodeStartToken)
        val interiorStopToken = formatter.previousSemanticRegion(nodeStopToken)

        if (interiorStopToken == nodeStartToken && interiorStartToken == nodeStopToken) {
            interiorStartToken = formatter.previousHiddenRegion(nodeStartToken)
        }

        if (interiorStartToken != null && interiorStopToken != null) {
            changes.add(Changes(interiorStartToken.startIndex, interiorStopToken.stopIndex, blockIndent = true))
        }
    }

    override fun interiorIndentRange(startNode: ParserRuleContext?, endNode: ParserRuleContext?) {
        if (startNode == null || endNode == null) return
        val nodeStartToken = startNode.start
        val nodeStopToken = endNode.stop

        val interiorStartToken = formatter.nextSemanticRegion(nodeStartToken)
        val interiorStopToken = formatter.previousSemanticRegion(nodeStopToken)

        if (interiorStartToken != null && interiorStopToken != null) {
            changes.add(Changes(interiorStartToken.startIndex, interiorStopToken.stopIndex, blockIndent = true))
        }
    }

    override fun interiorIndentRangeIncludeLast(startNode: ParserRuleContext?, endNode: ParserRuleContext?) {
        if (startNode == null || endNode == null) return
        val nodeStartToken = startNode.start
        val nodeStopToken = endNode.stop

        val interiorStartToken = formatter.nextSemanticRegion(nodeStartToken)
        if (interiorStartToken != null) {
            changes.add(Changes(interiorStartToken.startIndex, nodeStopToken.stopIndex, blockIndent = true))
        }
    }

    fun createReplacements(): List<TextReplacement> {
        val sortedChanges = changes.filter { !it.blockIndent }.sortedBy { it.startOffset }
        val blockIndents = changes.filter { it.blockIndent }
            .map { Interval.of(it.startOffset, it.stopOffset) }

        val resolvedChanges = mutableListOf<Changes>()
        var previous: Changes? = null
        sortedChanges.forEach { changes ->
            if (overlap(previous, changes)) {
                previous?.mergeValuesFrom(changes)
            } else {
                resolvedChanges.add(changes)
                previous = changes
            }
        }

        //Resolves what indentation level to use changes with indentations
        for (resolvedChange in resolvedChanges) {
            val interval = Interval.of(resolvedChange.startOffset, resolvedChange.stopOffset)
            val indentCount = countIndents(interval, blockIndents)
            if (indentCount > 0) {
                val indentText = " ".repeat(4 * indentCount)
                var replacementText = resolvedChange.getTextChanges()
                val lastNewLineIndex = replacementText.lastIndexOf("\n")
                if (lastNewLineIndex >= 0 && resolvedChange.newLineCount != null) {
                    val replacement = replacementText.substring(lastNewLineIndex + 1, replacementText.length)
                    replacementText = "\n".repeat(resolvedChange.newLineCount!!) + replacement + indentText
                    resolvedChange.setTextChanges(replacementText)
                }
            }
        }
        val replacements = resolvedChanges.map {
            TextReplacement(it.startOffset, it.stopOffset, it.getTextChanges())
        }
        return replacements
    }

    private fun countIndents(interval: Interval, blockIndents: List<Interval>): Int {
        var count = 0
        for (blockIndent in blockIndents) {
            if (blockIndent.properlyContains(interval)) {
                count++
            }
        }
        return count
    }

    private fun hiddenRegionChangeAppendModifier(change: Changes, appendAfterToken: Token) {
        val nextHiddenRegion = formatter.nextHiddenRegion(appendAfterToken)

        if (nextHiddenRegion != null && startsAfter(
                appendAfterToken,
                nextHiddenRegion
            ) && nextHiddenRegion.type == RellLexer.RULE_WS
        ) {
            change.startOffset = nextHiddenRegion.startIndex
            change.stopOffset = nextHiddenRegion.startIndex + nextHiddenRegion.text.length
            change.previousHiddenText = nextHiddenRegion.text
        }
    }

    private fun hiddenRegionChangePrependModifier(change: Changes, prependAfterToken: Token) {
        val prevHiddenRegion = formatter.previousHiddenRegion(prependAfterToken)

        if (prevHiddenRegion != null && startsBefore(
                prependAfterToken,
                prevHiddenRegion
            ) && prevHiddenRegion.type == RellLexer.RULE_WS
        ) {
            change.startOffset = prevHiddenRegion.startIndex
            change.stopOffset = prevHiddenRegion.startIndex + prevHiddenRegion.text.length
            change.previousHiddenText = prevHiddenRegion.text
        }
    }

    private fun overlap(first: Changes?, second: Changes?): Boolean {
        if (first == null || second == null) return false
        return second.startOffset <= first.stopOffset
    }

    private fun startsAfter(first: Token?, second: Token?): Boolean {
        if (first != null && second != null) {
            return first.stopIndex + 1 == second.startIndex
        }
        return false
    }

    private fun startsBefore(first: Token?, second: Token?): Boolean {
        if (first != null && second != null) {
            return first.startIndex - 1 == second.stopIndex
        }
        return false
    }
}
