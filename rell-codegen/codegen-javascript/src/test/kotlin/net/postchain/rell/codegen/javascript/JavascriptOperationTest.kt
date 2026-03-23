/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.javascript

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

class JavascriptOperationTest {
    companion object : SingleFileRellApp("operations") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @Test
    fun basicSyntaxTest() {
        val op = kotlin.test.assertNotNull(testModule.operations["input_parameter_multi"])
        val k = JavascriptOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("export function inputParameterMultiOperation(s, s2)")
            contains("return { name: \"input_parameter_multi\", args: [s, s2] }")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> Javascript: {1}")
    @CsvSource(
            "my_ns1.op1_in_namespace,myNs1Op1InNamespaceOperation",
            "my_ns1.my_ns2.op2_in_namespace,myNs1MyNs2Op2InNamespaceOperation",
            "my_ns1.my_ns2.op_3_in_namespace,myNs1MyNs2Op3InNamespaceOperation"
    )
    fun namespaceTest(rellQualifiedOpName: String, javascriptQualifiedOpName: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[rellQualifiedOpName])
        val k = JavascriptOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("export function $javascriptQualifiedOpName()")
            contains("return { name: \"$rellQualifiedOpName\" }")
        }
    }

    @ParameterizedTest(name = "operation {0} should contain params {1} with type-checks {2}")
    @CsvSource(
            "input_parameter_nargs,'','',''",
            "input_parameter_text,t,t,assertString(t)",
            "input_parameter_integer,i,i,assertNumber(i)",
            "input_parameter_big_integer,i,i,assertBigInteger(i)",
            "input_parameter_byte_array,b,b,assertBuffer(b)",
            "input_parameter_enum,e,e,assertNumber(e)",
            "input_parameter_boolean,b,b,assertNumber(b)",
            "input_parameter_rowid,r,r,assertNumber(r)",
            "input_parameter_pubkey,pubkey,pubkey,assertBuffer(pubkey)",
            "input_parameter_nullable_pubkey,pubkey,pubkey,if (pubkey != null) assertBuffer(pubkey)",
            "input_parameter_blockchain_rid,blockchainRid,blockchainRid,assertBuffer(blockchainRid)",
            "input_parameter_nullable_blockchain_rid,blockchainRid,blockchainRid,if (blockchainRid != null) assertBuffer(blockchainRid)",
            "input_parameter_gtv,g,g,assertAny(g)",
            "input_parameter_nullable_gtv,g,g,if (g != null) assertAny(g)",
            "input_parameter_list_gtv,g,g,assertArray(g)",
            "input_parameter_list_string,l,l,assertArray(l)",
            "input_parameter_set_gtv,g,Array.from(g),assertSet(g)",
            "input_parameter_entity,e,e,assertNumber(e)",
            "input_parameter_struct,s,Object.values(s),assertObject(s)",
            "input_parameter_list_input,v,v,assertArray(v)",
            "input_parameter_nullable_list_input,v,v,if (v != null) assertArray(v)",
            "input_parameter_set_input,v,Array.from(v),assertSet(v)",
            "input_parameter_set_string,s,Array.from(s),assertSet(s)",
            "input_parameter_map_text_bytearray,m,m,assertObject(m)",
            "input_parameter_map_text_gtv,m,m,assertObject(m)",
            "input_parameter_map_integer_text,m,m,assertArray(m)",
            "input_parameter_map_gtv_text,m,m,assertArray(m)",
            "input_parameter_map_gtv_gtv,m,m,assertArray(m)",
            "input_parameter_map_enum_text,m,m,assertArray(m)",
            "input_parameter_multi,'s, s2','s, s2',assertString(s)"

    )
    fun parameterTypeTest(opName: String, params: String, gtvParam: String, assertFun: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[opName])
        val k = JavascriptOperation(op)
        val formatted = k.format()
        val functionParams = params.ifEmpty { "" }
        // For multi-parameter case, the full assert check is in multiParameterAssertTest
        val fullAssertFun = if (opName == "input_parameter_multi") "assertString(s)\n\tassertString(s2)" else assertFun
        assertThat(formatted).all {
            contains("export function ${opName.snakeToLowerCamelCase()}Operation($functionParams) {")
            contains(fullAssertFun)
            if (functionParams.isEmpty()) {
                contains("return { name: \"$opName\" };")
            } else {
                contains("return { name: \"$opName\", args: [$gtvParam] };")
            }
        }
    }
}
