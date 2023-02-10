package net.postchain.rell.codegen.kotlin

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

internal class SimpleKotlinStructTest {

    companion object : SingleFileRellApp("structures") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @ParameterizedTest(name = "rell type {0} becomes {1}")
    @CsvSource(
        "boolean,Boolean",
        "integer,Long",
        "decimal,BigDecimal",
        "text,String",
        "byte_array,WrappedByteArray",
        "rowid,RowId",
        "entity,RowId",
        "map,Map<String, Long>",
        "nullable,String?",
        "json,String",
    )
    fun simpleStructures(rellType: String, kotlinType: String) {
        format(rellType, kotlinType)
    }

    @ParameterizedTest(name = "rell: {0} -> kotlin: {1}")
    @CsvSource(
        "my_ns1.ns_struct1,MyNs1NsStruct1",
        "my_ns1.my_ns2.ns_struct2,MyNs1MyNs2NsStruct2",
        "my_ns1.my_ns2.ns_struct_3,MyNs1MyNs2NsStruct3"
    )
    fun namespaceTest(rellQualifiedName: String, kotlinQualifiedName: String) {
        val struct = assertNotNull(testModule.structs[rellQualifiedName], "struct does not exist")
        val formatted = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains("data class $kotlinQualifiedName")
        }
    }

    @Test
    fun nullableAnnotation() {
        val formatted = format("nullable", "String?")
        assertThat(formatted).contains("@Nullable")
    }

    private fun format(rellType: String, kotlinType: String): String {
        val struct = assertNotNull(testModule.structs["${rellType}_struct"], "struct does not exist")
        val formatted = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains("val a: $kotlinType")
        }
        return formatted
    }

    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
        "name,String",
        "pubkey,WrappedByteArray",
        "blockchainRid,WrappedByteArray",
    )
    fun builtinTypes(keyword: String, kotlinType: String) {
        val struct = assertNotNull(testModule.structs["builtin_${keyword}"], "struct does not exist")
        val formatted = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains("val $keyword: $kotlinType")
        }
    }

    @Test
    fun nested() {
        val struct = assertNotNull(testModule.structs["nested_struct"])
        val k = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("val a: TextStruct")
        }
    }
}
