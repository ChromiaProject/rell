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
        "boolean,'val a: Boolean'",
        "integer,'val a: Long'",
        "decimal,'val a: BigDecimal'",
        "text,'val a: String'",
        "byte_array,'val a: WrappedByteArray'",
        "rowid,'val a: RowId'",
        "entity,'val a: RowId'",
        "map,'val a: Map<String, Long>'",
        "nullable,'val a: String?'",
        "json,'val a: String'",
        "list,'val a: List<String>'",
        "set,'val a: Set<String>'",
        "multi,'\n\t@Name(\"a\") val a: String,\n\t@Name(\"i\") val i: Long'",
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
            contains(kotlinType)
        }
        return formatted
    }

    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
            "name,String",
            "pubkey,WrappedByteArray",
            "blockchainRid,WrappedByteArray",
            "transaction, RowId",
            "block, RowId",
            "blockStruct, Block",
            "transactionStruct, Transaction",
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
