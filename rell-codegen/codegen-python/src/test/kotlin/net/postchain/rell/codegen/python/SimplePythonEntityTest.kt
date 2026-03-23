/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

class SimplePythonEntityTest {

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
        val k = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("@dataclass")
            contains("class TestEntity:")
            contains("\tname: str")
            contains("\tnum: int")
            contains("\tb_type: bool")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> python: {1}")
    @CsvSource(
            "my_ns1.ns_entity1,MyNs1NsEntity1",
            "my_ns1.my_ns2.ns_entity2,MyNs1MyNs2NsEntity2",
            "my_ns1.my_ns2.ns_entity_3,MyNs1MyNs2NsEntity3"
    )
    fun namespaceTest(rellQualifiedName: String, pythonQualifiedName: String) {
        val entity = assertNotNull(testModule.entities[rellQualifiedName])
        val k = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("class $pythonQualifiedName")
        }
    }

    @Test
    fun text() {
        val entity = assertNotNull(testModule.entities["multi_text_entity"])
        val k = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("name: str")
            contains("a: str")
            contains("b: str")
        }
    }

    @ParameterizedTest(name = "rell type {0} becomes {1}")
    @CsvSource(
            "a,boolean,bool",
            "a,integer,int",
            "a,big_integer,BigInt",
            "a,decimal,float",
            "a,text,str",
            "a,byte_array,bytes",
            "a,rowid,int",
            "a,json,str",
            "pubkey,pubkey,bytes",
            "blockchain_rid,blockchain_rid,bytes"
    )
    fun simpleEntities(fieldName: String, rellType: String, pythonType: String) {
        val entity = assertNotNull(testModule.entities["${rellType}_entity"], "entity does not exist")
        val k = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("$fieldName: $pythonType")
        }
    }

    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
            "name,str",
            "pubkey,bytes",
            "timestamp,int",
    )
    fun builtinTypes(keyword: String, pythonBuiltinType: String) {
        val entity = assertNotNull(testModule.entities["builtin_${keyword}"], "entity does not exist")
        val k = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("$keyword: $pythonBuiltinType")
        }
    }

    @Test
    fun nested() {
        val entity = assertNotNull(testModule.entities["nested_entity"])
        val k = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("a: int")
        }
    }

    @Test
    fun transaction() {
        val entity = assertNotNull(testModule.entities["transaction_entity"])
        val k = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("class TransactionEntity")
            contains("transaction: int")
        }
    }
}
