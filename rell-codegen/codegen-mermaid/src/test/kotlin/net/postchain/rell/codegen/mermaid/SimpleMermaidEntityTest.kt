package net.postchain.rell.codegen.mermaid

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import net.postchain.rell.codegen.MermaidClass
import net.postchain.rell.codegen.MermaidEntityReference
import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

internal class SimpleMermaidEntityTest {

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
        val k = MermaidEntityReference(entity)
        val formatted = k.format()
        assertThat(formatted.split("\n")).containsAll(
                "\t",
                "\ttest_entity {",
                "\t\ttext name  ",
                "\t\tinteger num  ",
                "\t\tboolean b_type  ",
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
        val k = MermaidEntityReference(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains(mermaidName)
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
        val k = MermaidEntityReference(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("$rellType $fieldName")
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
        val k = MermaidEntityReference(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("$rellType $fieldName")
        }
    }


    @Test
    fun nested() {
        val entity = assertNotNull(testModule.entities["nested_entity"])
        val k = MermaidEntityReference(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("\tnested_entity || -- |{ text_entity")
            contains("\t\ttext_entity a FK")
        }
    }

    @Test
    fun transaction() {
        val entity = assertNotNull(testModule.entities["transaction_entity"])
        val k = MermaidEntityReference(entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("\t\ttransaction transaction FK, UK \"index\"")
        }
    }
}
