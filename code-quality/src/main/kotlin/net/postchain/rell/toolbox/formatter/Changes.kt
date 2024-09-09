package net.postchain.rell.toolbox.formatter

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.max
import kotlin.math.min

class Changes(
    var startOffset: Int,
    var stopOffset: Int,
    val formatterOptions: FormatterOptions,
    var priority: ChangePriority = ChangePriority.DEFAULT,
    var indentationIncrease: Int? = null,
    var newLineDefault: Int? = null,
    var newLineMax: Int? = null,
    var newLineMin: Int? = null,
    var space: String? = null,
    var previousHiddenText: String? = null,
    val blockIndent: Boolean = false,
    var indentedText: String? = null,
    var newLineCount: Int? = null
) {

    fun setNewLines(newLines: Int) {
        setNewLines(newLines, newLines, newLines)
    }

    fun setNewLines(minNewLines: Int, defaultNewLines: Int, maxNewLines: Int) {
        newLineMin = minNewLines
        newLineDefault = defaultNewLines
        newLineMax = maxNewLines
    }

    fun noSpace() {
        setSpaces("")
    }

    fun oneSpace() {
        setSpaces(" ")
    }

    private fun setSpaces(spaces: String) {
        space = spaces
    }

    fun superHighPriority() {
        priority = ChangePriority.SUPER_HIGH
    }

    fun highPriority() {
        priority = ChangePriority.HIGH
    }

    fun lowPriority() {
        priority = ChangePriority.LOW
    }

    fun indent(indentation: Int? = null) {
        val inc = indentationIncrease
        indentationIncrease = if (inc == null) 1 else inc + 1
        if (indentation != null) {
            indentationIncrease = indentationIncrease?.plus(indentation)
        }
    }

    fun getTextChanges(): String {
        if (indentedText != null) {
            return indentedText!!
        }
        newLineCount = calculateNewLines()

        var textChange = ""
        if (space != null) {
            textChange = textChange.plus(space)
        }

        if (newLineCount != null && newLineCount!! > 0) {
            textChange = textChange.plus(formatterOptions.newLineString.repeat(newLineCount!!))
        }

        if (indentationIncrease != null) {
            val indentationString = if (formatterOptions.insertSpaces) {
                " ".repeat(formatterOptions.tabSize * indentationIncrease!!)
            } else {
                "\t".repeat(indentationIncrease!!)
            }
            textChange = textChange.plus(indentationString)
        }

        return textChange
    }

    private fun calculateNewLines(): Int? {
        val hiddenRegionNewLines = previousHiddenText?.count { it == '\n' } ?: return newLineDefault

        return if (newLineMax != null && hiddenRegionNewLines >= newLineMax!!) {
            newLineMax
        } else if (newLineMin != null && hiddenRegionNewLines < newLineMin!!) {
            newLineMin
        } else {
            newLineDefault
        }
    }

    private fun <T> merge(first: T?, second: T?, strategy: Int, propertyname: String): T? {
        if (first != null && second != null) {
            if (first == second || strategy < 0) {
                return first
            }
            if (strategy > 0) {
                return second
            }
            logger.warn {
                "Conflicting values for '$propertyname': '$first' and '$second'. " +
                    "Offset region of conflict: $startOffset - $stopOffset."
            }
            return null
        }
        return first ?: second
    }

    fun mergeValuesFrom(other: Changes) {
        val strategy = other.priority.priority.compareTo(priority.priority)
        space = merge(space, other.space, strategy, "space")
        newLineMin = merge(newLineMin, other.newLineMin, strategy, "newLineMin")
        newLineDefault = merge(newLineDefault, other.newLineDefault, strategy, "newLineDefault")
        newLineMax = merge(newLineMax, other.newLineMax, strategy, "newLineMax")
        priority = merge(priority, other.priority, strategy, "priority") ?: ChangePriority.DEFAULT

        indentationIncrease = if (indentationIncrease != null && other.indentationIncrease != null) {
            indentationIncrease!! + other.indentationIncrease!!
        } else {
            if (indentationIncrease != null) indentationIncrease else other.indentationIncrease
        }

        startOffset = min(startOffset, other.startOffset)
        stopOffset = max(stopOffset, other.stopOffset)
    }

    fun newLine() {
        setNewLines(1)
    }

    fun setTextChanges(indentedText: String?) {
        this.indentedText = indentedText
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
