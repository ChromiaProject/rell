/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.profiler

/**
 * Butterfly / reverse-call-tree view computed from async-profiler `collapsed` output.
 *
 * Each line in collapsed output is `frame1;frame2;...;leaf SAMPLES`, where samples is the
 * number of times that exact stack was sampled with `leaf` as the terminal frame (self time).
 * Hot-leaf butterfly: pick the top-K leaves by self samples; for each, walk *up* the stacks
 * grouping by immediate caller, then caller's caller, etc., to a bounded depth.
 *
 * Output format mirrors IntelliJ IDEA's "Backtraces" panel: top row is the hot leaf, indented
 * rows below are the callers (`<-`), sorted by sample count, deeper = further from leaf.
 * Branches contributing less than `callerThresholdPct` of the leaf's self samples are pruned
 * so an LLM can read the result without drowning in 0.x% noise.
 */
internal object Butterfly {

    fun render(
        collapsedText: String,
        topLeaves: Int,
        maxDepth: Int,
        callerThresholdPct: Double,
    ): String {
        val stacks = parseCollapsed(collapsedText)
        if (stacks.isEmpty()) return "(no samples)\n"

        val totalSamples = stacks.sumOf { it.samples }
        val selfHot = stacks
            .groupingBy { it.frames.last() }
            .fold(0L) { acc, s -> acc + s.samples }
            .toList()
            .sortedByDescending { it.second }
            .take(topLeaves)

        val sb = StringBuilder()
        sb.appendLine("--- Butterfly view (top $topLeaves self-hot methods, callers up to depth $maxDepth) ---")
        sb.appendLine("Total samples: $totalSamples")
        sb.appendLine("Format: header row is the hot leaf (self %); indented `<-` rows are callers,")
        sb.appendLine("        deeper indent = further from the leaf. Branches under " +
            "%.1f%% of the leaf's self time are pruned.".format(callerThresholdPct))
        sb.appendLine()

        for ((leaf, leafSelf) in selfHot) {
            val matching = stacks.filter { it.frames.last() == leaf }
            val pruneAt = ((leafSelf * callerThresholdPct / 100.0).toLong()).coerceAtLeast(1)
            val node = buildNode(matching, depth = 0, frame = leaf, maxDepth, pruneAt)
            renderNode(node, totalSamples, leafSelf, indent = 0, sb)
            sb.appendLine()
        }
        return sb.toString()
    }

    private data class Stack(val frames: List<String>, val samples: Long)

    private data class Node(
        val frame: String,
        val samples: Long,
        val children: List<Node>,
    )

    private fun parseCollapsed(text: String): List<Stack> = text.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val sp = line.lastIndexOf(' ')
            if (sp <= 0) return@mapNotNull null
            val count = line.substring(sp + 1).trim().toLongOrNull() ?: return@mapNotNull null
            val frames = line.substring(0, sp).split(';').filter { it.isNotEmpty() }
            if (frames.isEmpty()) null else Stack(frames, count)
        }
        .toList()

    private fun buildNode(
        matching: List<Stack>,
        depth: Int,
        frame: String,
        maxDepth: Int,
        pruneAt: Long,
    ): Node {
        val totalHere = matching.sumOf { it.samples }
        if (depth >= maxDepth) return Node(frame, totalHere, emptyList())

        val grouped = LinkedHashMap<String, MutableList<Stack>>()
        for (s in matching) {
            val callerIdx = s.frames.size - 2 - depth
            if (callerIdx < 0) continue
            grouped.getOrPut(s.frames[callerIdx]) { mutableListOf() }.add(s)
        }
        val children = grouped.entries
            .map { (caller, sub) -> caller to sub.sumOf { it.samples } }
            .filter { it.second >= pruneAt }
            .sortedByDescending { it.second }
            .map { (caller, _) ->
                buildNode(grouped[caller]!!, depth + 1, caller, maxDepth, pruneAt)
            }
        return Node(frame, totalHere, children)
    }

    private fun renderNode(node: Node, totalSamples: Long, leafSamples: Long, indent: Int, out: StringBuilder) {
        val pctOfTotal = node.samples * 100.0 / totalSamples
        val pctOfLeaf = if (leafSamples > 0) node.samples * 100.0 / leafSamples else 0.0
        val pad = "  ".repeat(indent)
        val arrow = if (indent == 0) "" else "<- "
        if (indent == 0) {
            out.appendLine("%5.2f%% of total  %5d samples  %s".format(pctOfTotal, node.samples, node.frame))
        } else {
            out.appendLine("%s%s%5.2f%% of leaf  %5d  %s".format(pad, arrow, pctOfLeaf, node.samples, node.frame))
        }
        for (child in node.children) renderNode(child, totalSamples, leafSamples, indent + 1, out)
    }
}
