/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import kotlin.system.exitProcess

/**
 * Concatenate two or more kotlinx-benchmark JSON result arrays into a single array,
 * preserving every field. Same-shape JMH records, just stitched together end-to-end.
 *
 * Usage:
 *   merge --output <merged.json> <input1.json> <input2.json> [...]
 *
 * Optionally tag each input's records with a `source` param so the renderer can
 * tell them apart when benchmark names collide:
 *   merge --output <merged.json> --tag old=<old.json> --tag new=<new.json>
 */
fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    val mapper = jacksonObjectMapper()

    val merged = mapper.createArrayNode()
    for ((tag, path) in parsed.inputs) {
        val file = File(path)
        require(file.isFile) { "Input JSON does not exist: $file" }
        val node = mapper.readTree(file)
        require(node is ArrayNode) { "Expected JSON array in $file, got ${node.nodeType}" }

        for (record in node) {
            require(record is ObjectNode) { "Expected JSON object in array; got ${record.nodeType}" }
            if (tag != null) record.tagSource(mapper, tag)
            merged.add(record)
        }
    }

    require(merged.size() > 0) { "Merged result is empty" }

    val out = File(parsed.output)
    out.parentFile?.mkdirs()
    mapper.writerWithDefaultPrettyPrinter().writeValue(out, merged)
    println("Wrote merged JSON: ${out.absolutePath} (${merged.size()} records)")
}

private fun ObjectNode.tagSource(mapper: com.fasterxml.jackson.databind.ObjectMapper, tag: String) {
    val params = (get("params") as? ObjectNode) ?: mapper.createObjectNode().also { set<JsonNode>("params", it) }
    params.put("source", tag)
}

private data class MergeArgs(val output: String, val inputs: List<Pair<String?, String>>)

private fun parseArgs(args: Array<String>): MergeArgs {
    var output: String? = null
    val inputs = mutableListOf<Pair<String?, String>>()
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--output", "-o" -> output = args.getOrNull(++i)
            "--tag" -> {
                val spec = args.getOrNull(++i) ?: error("Missing value for --tag")
                val eq = spec.indexOf('=')
                require(eq > 0) { "Expected --tag <label>=<path>, got: $spec" }
                inputs += spec.substring(0, eq) to spec.substring(eq + 1)
            }
            "-h", "--help" -> {
                println(
                    """
                    Usage: merge --output <merged.json> [--tag <label>=<path>]... [<path>]...
                       --tag adds params.source=<label> to every record from <path>
                    """.trimIndent(),
                )
                exitProcess(0)
            }
            else -> {
                require(!a.startsWith("--")) { "Unknown argument: $a" }
                inputs += null to a
            }
        }
        i++
    }
    return MergeArgs(
        output = checkNotNull(output) { "Missing --output" },
        inputs = inputs.also { require(it.size >= 2) { "Need at least two inputs to merge; got ${it.size}" } },
    )
}
