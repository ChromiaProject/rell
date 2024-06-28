package net.postchain.rell.codegen.typescript

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

internal class SimpleTypescriptEntityTest {

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
        val k = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("type TestEntity = ")
            contains("name: string;")
            contains("num: number;")
            contains("b_type: number;")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> typescript: {1}")
    @CsvSource(
            "my_ns1.ns_entity1,MyNs1NsEntity1",
            "my_ns1.my_ns2.ns_entity2,MyNs1MyNs2NsEntity2",
            "my_ns1.my_ns2.ns_entity_3,MyNs1MyNs2NsEntity3"
    )
    fun namespaceTest(rellQualifiedName: String, typescriptQualifiedName: String) {
        val entity = assertNotNull(testModule.entities[rellQualifiedName])
        val k = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("type $typescriptQualifiedName")
        }
    }

    @Test
    fun text() {
        val entity = assertNotNull(testModule.entities["multi_text_entity"])
        val k = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("name: string;")
            contains("a: string;")
            contains("b: string;")
        }
    }

    @ParameterizedTest(name = "rell type {0} becomes {1}")
    @CsvSource(
            "a,boolean,number",
            "a,integer,number",
            "a,big_integer,bigint",
            "a,decimal,number",
            "a,text,string",
            "a,byte_array,Buffer",
            "a,rowid,number",
            "a,json,string",
            "pubkey,pubkey,Buffer",
            "blockchain_rid,blockchain_rid,Buffer"
    )
    fun simpleEntities(fieldName: String, rellType: String, typescriptType: String) {
        val entity = assertNotNull(testModule.entities["${rellType}_entity"], "entity does not exist")
        val k = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("$fieldName: $typescriptType;")
        }
    }

    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
            "name,string",
            "pubkey,Buffer",
            "timestamp,number",
    )
    fun builtinTypes(keyword: String, typescriptType: String) {
        val entity = assertNotNull(testModule.entities["builtin_${keyword}"], "entity does not exist")
        val k = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("$keyword: $typescriptType;")
        }
    }

    @Test
    fun nested() {
        val entity = assertNotNull(testModule.entities["nested_entity"])
        val k = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("a: number;")
        }
    }

    @Test
    fun transaction() {
        val entity = assertNotNull(testModule.entities["transaction_entity"])
        val k = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("type TransactionEntity")
            contains("transaction: number;")
        }
    }
}
