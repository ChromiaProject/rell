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

class SimpleTypescriptStructTest {

    companion object : SingleFileRellApp("structures") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @ParameterizedTest(name = "rell type {0} becomes {1}")
    @CsvSource(
            "boolean,'a: boolean'",
            "integer,'a: number'",
            "decimal,'a: number'",
            "text,'a: string'",
            "byte_array,'a: Buffer'",
            "rowid,'a: number'",
            "entity,'a: number'",
            "map,'a: {[x: string]: number}'",
            "nullable,'a: string | null'",
            "json,'a: string'",
            "list,'a: string[]'",
            "set,'a: Set<string>'",
            "multi,'\n\ta: string;\n\ti: number;'",
    )
    fun simpleStructures(rellType: String, typescriptType: String) {
        format(rellType, typescriptType)
    }

    @ParameterizedTest(name = "rell: {0} -> Typescript: {1}")
    @CsvSource(
            "my_ns1.ns_struct1,MyNs1NsStruct1",
            "my_ns1.my_ns2.ns_struct2,MyNs1MyNs2NsStruct2",
            "my_ns1.my_ns2.ns_struct_3,MyNs1MyNs2NsStruct3"
    )
    fun namespaceTest(rellQualifiedName: String, kotlinQualifiedName: String) {
        val struct = assertNotNull(testModule.structs[rellQualifiedName], "struct does not exist")
        val formatted = TypescriptStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains("type $kotlinQualifiedName")
        }
    }

    @Test
    fun nullableAnnotation() {
        val formatted = format("nullable", "string")
        assertThat(formatted).contains(" | null")
    }

    private fun format(rellType: String, typescriptType: String): String {
        val struct = assertNotNull(testModule.structs["${rellType}_struct"], "struct does not exist")
        val formatted = TypescriptStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains(typescriptType)
        }
        return formatted
    }
    
    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
            "name,string",
            "pubkey,Buffer",
            "blockchainRid,Buffer",
            "transaction, number",
            "block, number",
            "blockStruct, Block",
            "transactionStruct, Transaction",
    )
    fun builtinTypes(keyword: String, typescriptType: String) {
        val struct = assertNotNull(testModule.structs["builtin_${keyword}"], "struct does not exist")
        val formatted = TypescriptStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains("$keyword: $typescriptType")

        }
    }

    @Test
    fun nested() {
        val struct = assertNotNull(testModule.structs["nested_struct"])
        val k = TypescriptStruct(CamelCaseClassName.fromRellDefinition(struct), struct)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("a: TextStruct")
        }
    }
}
