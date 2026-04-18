/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.mermaid

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import net.postchain.rell.codegen.MermaidClass
import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

internal class SimpleMermaidClassTest {

    companion object : SingleFileRellApp("entities") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @Test
    fun format() {
        val entity = assertNotNull(testModule.entities["test_entity"])
        val k = MermaidClass(entity)
        val formatted = k.format()
        assertThat(formatted.split("\n")).containsAtLeast(
                "\t",
                "\tclass test_entity {",
                "\t\tname: text",
                "\t\tnum: integer",
                "\t\tb_type: boolean",
                "\t}"
        )
    }

    @ParameterizedTest(name = "rell: {0} -> kotlin: {1}")
    @CsvSource(
            "my_ns1.ns_entity1,my_ns1_ns_entity1",
            "my_ns1.my_ns2.ns_entity2,my_ns1_my_ns2_ns_entity2",
            "my_ns1.my_ns2.ns_entity_3,my_ns1_my_ns2_ns_entity_3"
    )
    fun namespaceTest(rellQualifiedName: String, mermaidName: String) {
        val entity = assertNotNull(testModule.entities[rellQualifiedName])
        val k = MermaidClass(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("class $mermaidName")
        }
    }

    @ParameterizedTest(name = "rell type {1} is kept")
    @CsvSource(
            "a,boolean",
            "a,integer",
            "a,big_integer",
            "a,decimal",
            "a,text",
            "a,byte_array",
            "a,rowid",
            "a,json",
    )
    fun simpleEntities(fieldName: String, rellType: String) {
        val entity = assertNotNull(testModule.entities["${rellType}_entity"], "entity does not exist")
        val k = MermaidClass(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("$fieldName: $rellType")
        }
    }

    @ParameterizedTest(name = "rell alias {1} is resolved to {2}")
    @CsvSource(
            "pubkey,pubkey,byte_array",
            "name,name,text",
            "timestamp,timestamp,integer",
    )
    fun aliasEntities(fieldName: String, alias: String, rellType: String) {
        val entity = assertNotNull(testModule.entities["builtin_${alias}"], "entity does not exist")
        val k = MermaidClass(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("$fieldName: $rellType")
        }
    }


    @Test
    fun nested() {
        val entity = assertNotNull(testModule.entities["nested_entity"])
        val k = MermaidClass(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("\ttext_entity <|-- nested_entity")
            contains("\t\t+a: text_entity")
        }
    }

    @Test
    fun transaction() {
        val entity = assertNotNull(testModule.entities["transaction_entity"])
        val k = MermaidClass(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("\ttransaction <|-- transaction_entity")
            contains("\t\t+transaction: transaction")
        }
    }
}
