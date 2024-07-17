package net.postchain.rell.toolbox.formatter
import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta
import com.github.difflib.patch.DeltaType


object Diff {
    fun diffStrings(original: String, revised: String): List<AbstractDelta<String>> {
        val originalLines = original.lines()
        val revisedLines = revised.lines()
        return DiffUtils.diff(originalLines, revisedLines, false).deltas
    }

    fun diffInline(original: String, revised: String): List<AbstractDelta<String>> {
        val patch = DiffUtils.diffInline(original, revised)
        return patch.deltas
    }

    fun applyAllChanges(original: String, deltas: List<AbstractDelta<String>>): String {
        var originalLines = original.lines().toMutableList()

        for (delta in deltas.reversed()) {
            originalLines = applyDelta(originalLines, delta)
        }

        return originalLines.joinToString("\n")
    }

    fun applyChange(original: String, deltas: List<AbstractDelta<String>>, index: Int): String {
        if (index !in deltas.indices) {
            throw IllegalArgumentException("Invalid index for delta")
        }

        val originalLines = original.lines().toMutableList()
        val delta = deltas[index]

        val modifiedLines = applyDelta(originalLines, delta)
        return modifiedLines.joinToString("\n")
    }

    private fun applyDelta(originalLines: MutableList<String>, delta: AbstractDelta<String>): MutableList<String> {
        when (delta.type) {
            DeltaType.INSERT -> {
                originalLines.addAll(delta.target.position, delta.target.lines)
            }
            DeltaType.DELETE -> {
                originalLines.subList(delta.source.position, delta.source.position + delta.source.lines.size).clear()
            }
            DeltaType.CHANGE -> {
                originalLines.subList(delta.source.position, delta.source.position + delta.source.lines.size).clear()
                originalLines.addAll(delta.source.position, delta.target.lines)
            }
            DeltaType.EQUAL -> {

            }
        }
        return originalLines
    }

}
