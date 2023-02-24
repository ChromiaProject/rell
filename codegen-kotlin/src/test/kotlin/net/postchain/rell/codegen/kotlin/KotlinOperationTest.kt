package net.postchain.rell.codegen.kotlin

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.endsWith
import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class KotlinOperationTest {

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
        val k = KotlinOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("fun TransactionBuilder.inputParameterTextOperation(t: String) =")
            contains("addOperation(\"input_parameter_text\", gtv(t))")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> kotlin: {1}")
    @CsvSource(
        "my_ns1.op1_in_namespace,myNs1Op1InNamespaceOperation",
        "my_ns1.my_ns2.op2_in_namespace,myNs1MyNs2Op2InNamespaceOperation",
        "my_ns1.my_ns2.op_3_in_namespace,myNs1MyNs2Op3InNamespaceOperation"
        // see namespaced arg test in [KotlinQueryTest]
    )
    fun namespaceTest(rellQualifiedOpName: String, kotlinQualifiedOpName: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[rellQualifiedOpName])
        val k = KotlinOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("fun TransactionBuilder.$kotlinQualifiedOpName() =")
            contains("addOperation(\"$rellQualifiedOpName\")")
        }
    }

    @ParameterizedTest(name = "operation {0} should contain params {1} with gtv-conversion {2}")
    @CsvSource(
        "input_parameter_nargs,'',''",
        "input_parameter_text,t: String,gtv(t)",
        "input_parameter_integer,i: Long,gtv(i)",
        "input_parameter_byte_array,b: ByteArray,gtv(b)",
        "input_parameter_enum,e: TestEnum,gtv(e.ordinal.toLong())",
        "input_parameter_boolean,b: Boolean,gtv(b)",
        "input_parameter_rowid,r: RowId,gtv(r.id)",
        "input_parameter_pubkey,pubkey: PubKey,gtv(pubkey.data)",
        "input_parameter_nullable_pubkey,pubkey: PubKey?,pubkey.let { if (it == null) GtvNull else gtv(it.data) }",
        "input_parameter_blockchain_rid,blockchainRid: BlockchainRid,gtv(blockchainRid)",
        "input_parameter_nullable_blockchain_rid,blockchainRid: BlockchainRid?,blockchainRid.let { if (it == null) GtvNull else gtv(it) }",
        "input_parameter_gtv,g: Gtv,g",
        "input_parameter_nullable_gtv,g: Gtv?,g.let { if (it == null) GtvNull else it }",
        "input_parameter_list_gtv,g: List<Gtv>,gtv(g)",
        "input_parameter_list_string,l: List<String>,gtv(l.map { gtv(it) })",
        "input_parameter_set_gtv,g: Set<Gtv>,gtv(g.toList())",
        "input_parameter_entity,e: RowId,gtv(e.id)",
        "input_parameter_struct,s: TestStruct,GtvObjectMapper.toGtvArray(s)",
        "input_parameter_list_input,v: List<ByteArray>,gtv(v.map { gtv(it) })",
        "input_parameter_nullable_list_input,v: List<ByteArray>?,v.let { if (it == null) GtvNull else gtv(it.map { gtv(it) }) }",
        "input_parameter_set_input,v: Set<ByteArray>,gtv(v.map { gtv(it) })",
        "input_parameter_set_string,s: Set<String>,gtv(s.map { gtv(it) })",
        "input_parameter_map_text_bytearray,'m: Map<String, ByteArray>',gtv(m.mapValues { gtv(it.value) })",
        "input_parameter_map_text_gtv,'m: Map<String, Gtv>',gtv(m)",
        "input_parameter_map_integer_text,'m: Map<Long, String>','gtv(m.map { (k, v) -> gtv(gtv(k), gtv(v)) })'",
        "input_parameter_map_gtv_text,'m: Map<Gtv, String>','gtv(m.map { (k, v) -> gtv(k, gtv(v)) })'",
        "input_parameter_map_gtv_gtv,'m: Map<Gtv, Gtv>','gtv(m.map { (k, v) -> gtv(k, v) })'",
        "input_parameter_map_enum_text,'m: Map<TestEnum, String>','gtv(m.map { (k, v) -> gtv(gtv(k.ordinal.toLong()), gtv(v)) })'",
    )
    fun parameterTypeTest(opName: String, params: String, gtvParam: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[opName])
        val k = KotlinOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("fun TransactionBuilder.")
            contains("($params) =")
            contains("addOperation(\"$opName\"")
            endsWith("$gtvParam)\n")
        }
    }
}