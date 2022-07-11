package net.postchain.rell.codegen.kotlin

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
        val k = KotlinOperation(op, "")
        val formatted = k.format()
        assertk.assert(formatted).all {
            contains("fun GTXTransactionBuilder.inputParameterTextOperation(t: String) =")
            contains("addOperation(\"input_parameter_text\", gtv(t))")
        }
    }

    @ParameterizedTest(name = "operation {0} should contain params {1} with gtv-conversion {2}")
    @CsvSource(
        "input_parameter_nargs,'',''",
        "input_parameter_text,t: String,gtv(t)",
        "input_parameter_integer,i: Long,gtv(i)",
        "input_parameter_enum,e: TestEnum,gtv(e.ordinal.toLong())",
        "input_parameter_boolean,b: Boolean,gtv(b)",
        "input_parameter_entity,e: Long,gtv(e)",
        "input_parameter_struct,s: TestStruct,s.toGtv()",
        "input_parameter_list_input,v: List<ByteArray>,gtv(v.map { gtv(it) })",
        "input_parameter_nullable_list_input,v: List<ByteArray>?,v.let { if (it == null) GtvNull else gtv(it.map { gtv(it) }) }",
        "input_parameter_set_input,v: Set<ByteArray>,gtv(v.map { gtv(it) })",
        "input_parameter_map_input,'v: Map<String, ByteArray>',gtv(v)",
    )
    fun parameterTypeTest(queryName: String, params: String, gtvParam: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[queryName])
        val k = KotlinOperation(op, "")
        val formatted = k.format()
        assertk.assert(formatted).all {
            contains("fun GTXTransactionBuilder.")
            contains("($params) =")
            contains("addOperation(\"$queryName\"")
            endsWith("$gtvParam)\n")
        }
    }
}