package net.postchain.rell.codegen.javascript

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

class SimpleJavascriptBuiltinTest {

    @Test
    fun unsupportedTypeCheck() {
        val anyAssertion = JavascriptBuiltinType.AnyAssertion.createBuiltin()
        val formatted = anyAssertion.format()
        assertThat(formatted).all {
            contains("""
                |/* Unsupported Rell type for JS assertion, defaults to true */
                |export function assertAny(arg) {
                |${"\t"}return true
                |}
            """.trimMargin())
        }
    }


    @ParameterizedTest(name = "Builtin type {0} should get typecheck of {1}")
    @CsvSource(
            "assertBoolean,boolean",
            "assertNumber,number",
            "assertString,string",
            "assertObject,object",
    )
    fun primitiveTypeCheckTest(builtin: String, jsType: String) {
        val builtinType = assertNotNull(JavascriptBuiltinType.values().firstOrNull { it.builtin.functionName == builtin }, "Builtin type does not exist")
        val formatted = builtinType.createBuiltin().format()
        assertThat(formatted).all {
            contains("""
                |function $builtin(arg) {
                |${"\t"}if(typeof arg !== "$jsType") throw new Error("Expected input to be $jsType")
                |}
            """.trimMargin())
        }
    }

    @ParameterizedTest(name = "Builtin type {0} should get typecheck of {1}")
    @CsvSource(
            "assertBuffer,Buffer",
            "assertSet,Set",
            "assertArray,Array",
    )
    fun complexTypeCheckTest(builtin: String, jsType: String) {
        val builtinType = assertNotNull(JavascriptBuiltinType.values().firstOrNull { it.builtin.functionName == builtin }, "Builtin type does not exist")
        val formatted = builtinType.createBuiltin().format()
        assertThat(formatted).all {
            contains("""
                |function $builtin(arg) {
                |${"\t"}if(!(arg instanceof ${jsType})) throw new Error("Expected input to be $jsType")
                |}
            """.trimMargin())
        }
    }
}
