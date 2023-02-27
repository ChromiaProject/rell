package net.postchain.rell.codegen.kotlin

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

internal class SimpleKotlinEntityTest {

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
        val k = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("TestEntity")
            contains("val name: String")
            contains("num: Long")
            contains("bType: Boolean")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> kotlin: {1}")
    @CsvSource(
        "my_ns1.ns_entity1,MyNs1NsEntity1",
        "my_ns1.my_ns2.ns_entity2,MyNs1MyNs2NsEntity2",
        "my_ns1.my_ns2.ns_entity_3,MyNs1MyNs2NsEntity3"
    )
    fun namespaceTest(rellQualifiedName: String, kotlinQualifiedName: String) {
        val entity = assertNotNull(testModule.entities[rellQualifiedName])
        val k = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("data class $kotlinQualifiedName")
        }
    }

    @Test
    fun text() {
        val entity = assertNotNull(testModule.entities["multi_text_entity"])
        val k = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("val name: String")
            contains("val a: String")
            contains("val b: String")
        }
    }

    @ParameterizedTest(name = "rell type {0} becomes {1}")
    @CsvSource(
        "a,boolean,Boolean",
        "a,integer,Long",
        "a,decimal,BigDecimal",
        "a,text,String",
        "a,byte_array,WrappedByteArray",
        "a,rowid,RowId",
        "a,json,String",
        "pubkey,pubkey,WrappedByteArray",
        "blockchainRid,blockchain_rid,WrappedByteArray"
    )
    fun simpleEntities(fieldName: String, rellType: String, kotlinType: String) {
        val entity = assertNotNull(testModule.entities["${rellType}_entity"], "entity does not exist")
        val k = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("val $fieldName: $kotlinType")
        }
    }

    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
        "name,String",
        "pubkey,WrappedByteArray",
        "timestamp,Long",
    )
    fun builtinTypes(keyword: String, kotlinType: String) {
        val entity = assertNotNull(testModule.entities["builtin_${keyword}"], "entity does not exist")
        val k = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("val $keyword: $kotlinType")
        }
    }

    @Test
    fun nested() {
        val entity = assertNotNull(testModule.entities["nested_entity"])
        val k = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("val a: RowId")
        }
    }

    @Test
    fun transaction(){
        val entity = assertNotNull(testModule.entities["transaction_entity"])
        val k = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("val transaction: RowId")
        }
    }
}
