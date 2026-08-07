/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import com.fasterxml.jackson.databind.ObjectMapper
import net.postchain.rell.base.runtime.Rt_JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [Rt_JsonNode] to `jackson-databind`'s `JsonNode`, which used to back Rell's `json` type. The text a node
 * renders to is stored in the database and passed to GTV, and the node type names appear in Rell error codes, so
 * the migration to Jackson's streaming API had to leave both untouched.
 *
 * Both implementations read through the same `JsonFactory`, so what is accepted and how strings and numbers are
 * written need no exhaustive coverage here - it is the same code. What is covered is what the migration reimplemented:
 * the tree, the number classification, and the decision to stop after the first root value.
 */
internal object JsonNodeJacksonCompatTest {
    private val mapper = ObjectMapper()

    private val INPUTS = listOf(
        // numbers, whose node type decides how they are rendered back
        "0", "-0", "1.0", "1.50", "1e3", "-0.0", "2147483648", "9223372036854775808",
        "123456789012345678901234567890", "1e400",
        // strings, containers, and a repeated key, which keeps its last value
        "\"a\"", "\"\\u0001\"", "\"\ud83d\ude00\"", "{}", "[]", "[1, 2]", "{\"a\":1,\"a\":2}",
        "{\"x\":1.50,\"y\":[1e3],\"z\":{\"w\":null}}", "null", "true", "false",
        // one root value is read and the rest of the input ignored
        "1 2", "[1]x", "{} {}",
        // rejected
        "01", "NaN", "'a'", "1[",
    )

    @Test fun testParseAndRender() {
        for (input in INPUTS) {
            assertEquals(jackson(input), rell(input), "input: <$input>")
        }
    }

    @Test fun testIntegerClassification() {
        val numbers = listOf(
            "0", "-0", "1", "-1", "2147483648", "9223372036854775807", "9223372036854775808",
            "-9223372036854775809", "123456789012345678901234567890", "1.0", "1e3", "0.5",
        )
        for (input in numbers) {
            val node = mapper.readTree(input)
            val actual = Rt_JsonNode.parse(input)
            assertEquals(node.isIntegralNumber, actual.isIntegral, "isIntegral: <$input>")
            assertEquals(node.isInt || node.isLong || node.isShort, actual.isLongNumber, "isLong: <$input>")
            assertEquals(node.isBigInteger, actual.isBigIntegerNumber, "isBigInteger: <$input>")
            if (node.isIntegralNumber) {
                assertEquals(node.bigIntegerValue(), actual.bigIntegerValue(), "bigIntegerValue: <$input>")
                assertEquals(node.asLong(), actual.asLong(), "asLong: <$input>")
            }
        }
    }

    private fun jackson(s: String): String = try {
        val node = mapper.readTree(s)
        if (node == null || node.isMissingNode) "error" else "${node.nodeType.name}:$node"
    } catch (_: Throwable) {
        "error"
    }

    private fun rell(s: String): String = try {
        val node = Rt_JsonNode.parse(s)
        "${node.nodeTypeName}:$node"
    } catch (_: Throwable) {
        "error"
    }
}
