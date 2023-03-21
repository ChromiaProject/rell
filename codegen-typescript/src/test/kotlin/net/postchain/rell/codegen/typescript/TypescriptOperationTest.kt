package net.postchain.rell.codegen.typescript

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.endsWith
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class TypescriptOperationTest {

    companion object : SingleFileRellApp("operations") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @Test
    fun basicSyntaxTest() {
        val op = kotlin.test.assertNotNull(testModule.operations["input_parameter_text"])
        val k = TypescriptOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("export function inputParameterTextOperation(tx: Itransaction,\n\tt: string): void")
            contains("tx.addOperation(\"input_parameter_text\", t)")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> Typescript: {1}")
    @CsvSource(
            "my_ns1.op1_in_namespace,myNs1Op1InNamespaceOperation",
            "my_ns1.my_ns2.op2_in_namespace,myNs1MyNs2Op2InNamespaceOperation",
            "my_ns1.my_ns2.op_3_in_namespace,myNs1MyNs2Op3InNamespaceOperation"
    )
    fun namespaceTest(rellQualifiedOpName: String, typescriptQualifiedOpName: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[rellQualifiedOpName])
        val k = TypescriptOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("export function $typescriptQualifiedOpName(tx: Itransaction): void")
            contains("tx.addOperation(\"$rellQualifiedOpName\")")
        }
    }

    @ParameterizedTest(name = "operation {0} should contain params {1} with type-conversion {2}")
    @CsvSource(
            "input_parameter_nargs,'',''",
            "input_parameter_text,t: string,t",
            "input_parameter_integer,i: number,i",
            "input_parameter_byte_array,b: Buffer,b",
            "input_parameter_enum,e: TestEnum,e",
            "input_parameter_boolean,b: boolean,b",
            "input_parameter_rowid,r: number,r",
            "input_parameter_pubkey,pubkey: Buffer,pubkey",
            "input_parameter_nullable_pubkey,pubkey: Buffer | null,pubkey",
            "input_parameter_blockchain_rid,blockchainRid: Buffer,blockchainRid",
            "input_parameter_nullable_blockchain_rid,blockchainRid: Buffer | null,blockchainRid",
            "input_parameter_gtv,g: any,g",
            "input_parameter_nullable_gtv,g: any | null,g",
            "input_parameter_list_gtv,g: any[],g",
            "input_parameter_list_string,l: string[],l",
            "input_parameter_set_gtv,g: Set<any>,Array.from(g)",
            "input_parameter_entity,e: number,e",
            "input_parameter_struct,s: TestStruct,s",
            "input_parameter_list_input,v: Buffer[],v",
            "input_parameter_nullable_list_input,v: Buffer[] | null,v",
            "input_parameter_set_input,v: Set<Buffer>,Array.from(v)",
            "input_parameter_set_string,s: Set<string>, Array.from(s)",
            "input_parameter_map_text_bytearray,'m: {[x: string]: Buffer}',m",
            "input_parameter_map_text_gtv,'m: {[x: string]: any}',m",
            "input_parameter_map_integer_text,'m: {[x: number]: string}',m",
            "input_parameter_map_gtv_text,'m: {[x: any]: string}',m",
            "input_parameter_map_gtv_gtv,'m: {[x: any]: any}',m",
            "input_parameter_map_enum_text,'m: {[x: TestEnum]: string}',m",
            "input_parameter_multi,'s: string,\n\ts2: string','s,\n\ts2'"
    )
    fun parameterTypeTest(opName: String, params: String, gtvParam: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[opName])
        val k = TypescriptOperation(op)
        val formatted = k.format()
        val functionParams = if (params.isEmpty()) "" else ",\n\t$params"
        assertThat(formatted).all {
            contains("export function ${opName.snakeToLowerCamelCase()}Operation(tx: Itransaction$functionParams): void {")
            contains("addOperation(\"$opName\"")
            endsWith("$gtvParam)\n}")
        }
    }
}