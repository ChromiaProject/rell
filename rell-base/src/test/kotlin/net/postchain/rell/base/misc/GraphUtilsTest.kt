/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.misc

import net.postchain.rell.base.compiler.base.utils.C_GraphUtils
import org.junit.Test
import kotlin.test.assertEquals

class GraphUtilsTest {
    @Test fun testFindCycles1() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B")
        graph["B"] = listOf("A")
        graph["C"] = listOf("D")
        graph["D"] = listOf()

        val cycles = C_GraphUtils.findCycles(graph)
        assertEquals("[[A, B]]", cycles.toString())
    }

    @Test fun testFindCycles2() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B", "C", "D")
        graph["B"] = listOf("A")
        graph["C"] = listOf("A")
        graph["D"] = listOf("A")

        val cycles = C_GraphUtils.findCycles(graph)
        assertEquals("[[A, B], [A, C], [A, D]]", cycles.map {it.sorted()}.sortedBy { it.toString() }.toString())
    }

    @Test fun testFindCyclicVertices_AllVerticesInOneCycle() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B")
        graph["B"] = listOf("C")
        graph["C"] = listOf("D")
        graph["D"] = listOf("E")
        graph["E"] = listOf("A")

        val cycles = C_GraphUtils.findCyclicVertices(graph)
        assertEquals("[A, B, C, D, E]", cycles.sorted().toString())
    }

    @Test fun testFindCyclicVertices_SingleVertexCycle() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("A")

        val cycles = C_GraphUtils.findCyclicVertices(graph)
        assertEquals("[A]", cycles.sorted().toString())
    }

    @Test fun testFindCyclicVertices_NoCycleChain() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B")
        graph["B"] = listOf("C")
        graph["C"] = listOf("D")
        graph["D"] = listOf("E")
        graph["E"] = listOf()

        val cycles = C_GraphUtils.findCyclicVertices(graph)
        assertEquals("[]", cycles.sorted().toString())
    }

    @Test fun testFindCyclicVertices_NoCycleOneToAll() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B", "C", "D", "E")
        graph["B"] = listOf()
        graph["C"] = listOf()
        graph["D"] = listOf()
        graph["E"] = listOf()

        val cycles = C_GraphUtils.findCyclicVertices(graph)
        assertEquals("[]", cycles.sorted().toString())
    }

    @Test fun testFindCyclicVertices_NoCycleManyToMany() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B", "C", "D", "E")
        graph["B"] = listOf("C", "D", "E")
        graph["C"] = listOf("D", "E")
        graph["D"] = listOf("E")
        graph["E"] = listOf()

        val cycles = C_GraphUtils.findCyclicVertices(graph)
        assertEquals("[]", cycles.sorted().toString())
    }

    @Test fun testFindCyclicVertices_MultipleCycles() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B", "C", "D", "E")
        graph["B"] = listOf("A")
        graph["C"] = listOf("A")
        graph["D"] = listOf("A")
        graph["E"] = listOf("A")

        val cycles = C_GraphUtils.findCyclicVertices(graph)
        assertEquals("[A, B, C, D, E]", cycles.sorted().toString())
    }

    @Test fun testFindCyclicVertices_MultipleDisjointCycles() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B")
        graph["B"] = listOf("A")
        graph["C"] = listOf("D")
        graph["D"] = listOf("C")
        graph["E"] = listOf("F")
        graph["F"] = listOf("E")

        val cycles = C_GraphUtils.findCyclicVertices(graph)
        assertEquals("[A, B, C, D, E, F]", cycles.sorted().toString())
    }

    @Test fun testFindCyclicVertices_Misc() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B", "D")
        graph["B"] = listOf("C")
        graph["C"] = listOf("A", "D")
        graph["D"] = listOf("E")
        graph["E"] = listOf()

        val cycles = C_GraphUtils.findCyclicVertices(graph)
        assertEquals("[A, B, C]", cycles.sorted().toString())
    }

    @Test fun testTopologicalSort() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B")
        graph["B"] = listOf("C")
        graph["C"] = listOf()

        val sort = C_GraphUtils.topologicalSort(graph)
        assertEquals("[C, B, A]", sort.toString())
    }

    @Test fun testTranspose() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B")
        graph["B"] = listOf("C")
        graph["C"] = listOf("D")
        graph["D"] = listOf("E")
        graph["E"] = listOf("A")

        val graph2 = C_GraphUtils.transpose(graph)
        assertEquals("{A=[E],B=[A],C=[B],D=[C],E=[D]}", graphToString(graph2))
    }

    @Test fun testTranspose_2() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf()

        val graph2 = C_GraphUtils.transpose(graph)
        assertEquals("{A=[]}", graphToString(graph2))
    }

    @Test fun testClosure() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B")
        graph["B"] = listOf("C")
        graph["C"] = listOf("D")
        graph["D"] = listOf("E")
        graph["E"] = listOf()

        chkClosure(graph, listOf("A"), "[A, B, C, D, E]")
        chkClosure(graph, listOf("B"), "[B, C, D, E]")
        chkClosure(graph, listOf("C"), "[C, D, E]")
        chkClosure(graph, listOf("D"), "[D, E]")
        chkClosure(graph, listOf("E"), "[E]")
        chkClosure(graph, listOf("A", "B", "C", "D", "E"), "[A, B, C, D, E]")
    }

    @Test fun testClosure_2() {
        val graph = mutableMapOf<String, List<String>>()
        graph["A"] = listOf("B")
        graph["B"] = listOf("C")
        graph["C"] = listOf()
        graph["D"] = listOf("E")
        graph["E"] = listOf()

        chkClosure(graph, listOf("A"), "[A, B, C]")
        chkClosure(graph, listOf("A", "B"), "[A, B, C]")
        chkClosure(graph, listOf("A", "B", "C"), "[A, B, C]")
        chkClosure(graph, listOf("B"), "[B, C]")
        chkClosure(graph, listOf("C"), "[C]")
        chkClosure(graph, listOf("A", "D"), "[A, B, C, D, E]")
    }

    private fun chkClosure(graph: Map<String, List<String>>, verts: List<String>, exp: String) {
        val closure = C_GraphUtils.closure(verts) { graph.getValue(it) }
        assertEquals(exp, closure.sorted().toString())
    }

    @Test fun testParentLinksToChildrenCollections() {
        val graph = mutableMapOf(
                "B" to listOf("A"),
                "C" to listOf("B"),
                "D" to listOf("B"),
                "E" to listOf()
        )

        val graph2 = C_GraphUtils.transpose(graph)
        assertEquals("{A=[B],B=[C,D],C=[],D=[],E=[]}", graphToString(graph2))
    }

    private fun graphToString(graph: Map<String, Collection<String>>): String {
        return "{" + graph.keys.sorted().joinToString(",") {
            "" + it + "=[" + graph[it]!!.sorted().joinToString(",") + "]"
        } + "}"
    }
}
