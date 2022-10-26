package net.postchain.rell.codegen.kotlin

import assertk.assertThat
import assertk.all
import assertk.assertions.contains
import assertk.assertions.endsWith
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
        "input_parameter_entity,e: RowId,gtv(e.id)",
        "input_parameter_struct,s: TestStruct,GtvObjectMapper.toGtvArray(s)",
        "input_parameter_list_input,v: List<ByteArray>,gtv(v.map { gtv(it) })",
        "input_parameter_nullable_list_input,v: List<ByteArray>?,v.let { if (it == null) GtvNull else gtv(it.map { gtv(it) }) }",
        "input_parameter_set_input,v: Set<ByteArray>,gtv(v.map { gtv(it) })",
        "input_parameter_map_input,'v: Map<String, ByteArray>',gtv(v.mapValues { gtv(it.value) })",
        "input_parameter_enum_map,'m: Map<TestEnum, String>','gtv(m.map { (k, v) -> gtv(gtv(k.ordinal.toLong()), gtv(v)) })'",
    )
    fun parameterTypeTest(queryName: String, params: String, gtvParam: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[queryName])
        val k = KotlinOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("fun TransactionBuilder.")
            contains("($params) =")
            contains("addOperation(\"$queryName\"")
            endsWith("$gtvParam)\n")
        }
    }
}