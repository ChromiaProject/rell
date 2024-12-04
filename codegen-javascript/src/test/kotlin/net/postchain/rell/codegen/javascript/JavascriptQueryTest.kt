package net.postchain.rell.codegen.javascript

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class JavascriptQueryTest {
    companion object : SingleFileRellApp("queries") {
        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @Test
    fun basicSyntaxTest() {
        val q = kotlin.test.assertNotNull(testModule.queries["input_parameter_nargs"])
        val k = JavascriptQuery(q)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("""
                |export function inputParameterNargsQueryObject() {
                |${"\t"}return { name: "input_parameter_nargs", args: undefined };
                |}""".trimMargin())
        }
    }

    @ParameterizedTest(name = "rell: {0} -> javascript: {1}")
    @CsvSource(
            "my_ns1.q1_in_namespace,myNs1Q1InNamespace,e",
            "my_ns1.q2_in_namespace,myNs1Q2InNamespace,s",
            "my_ns1.q3a_return_type_enum,myNs1Q3aReturnTypeEnum,e",
            "my_ns1.q3b_return_type_enum,myNs1Q3bReturnTypeEnum,m",
            "my_ns1.q4_return_type_list_struct,myNs1Q4ReturnTypeListStruct,m",
            "my_ns1.q5_return_type_list_struct,myNs1Q5ReturnTypeListStruct,v",
            "my_ns1.q6_return_type_list_struct,myNs1Q6ReturnTypeListStruct,''",
            "my_ns1.q7_return_type_enum_map,myNs1Q7ReturnTypeEnumMap,''",
            "my_ns1.q8_return_type_enum_map,myNs1Q8ReturnTypeEnumMap,''",
            "my_ns1.q9_return_type_any_map,myNs1Q9ReturnTypeAnyMap,''",
            "my_ns1.q10_return_type_any_map,myNs1Q10ReturnTypeAnyMap,''",
            "my_ns1.my_ns2.q2_in_namespace,myNs1MyNs2Q2InNamespace,''",
            "my_ns1.my_ns2.q_3_in_namespace,myNs1MyNs2Q3InNamespace,''"
    )
    fun namespaceTest(rellQualifiedName: String, javascriptQualifiedName: String, params: String) {
        val q = kotlin.test.assertNotNull(testModule.queries[rellQualifiedName])
        val k = JavascriptQuery(q)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("export function ${javascriptQualifiedName}QueryObject($params)")
            contains("return { name: \"$rellQualifiedName\", args: ${if(params == "") "undefined" else params } }")
        }
    }

    @ParameterizedTest(name = "query {0} should contain params {1} with type-checks {2}")
    @CsvSource(
            "input_parameter_nargs,'',''",
            "input_parameter_text,t,assertString(t)",
            "input_parameter_nullable,t,if (t != null) assertString(t)",
            "input_parameter_integer,i,assertNumber(i)",
            "input_parameter_big_integer,i,assertBigInteger(i)",
            "input_parameter_enum,e,assertNumber(e)",
            "input_parameter_boolean,b,assertNumber(b)",
            "input_parameter_rowid,r,assertNumber(r)",
            "input_parameter_pubkey,pubkey,assertBuffer(pubkey)",
            "input_parameter_blockchain_rid,blockchainRid,assertBuffer(blockchainRid)",
            "input_parameter_entity,e,assertNumber(e)",
            "input_parameter_struct,s,assertObject(s)",
            "input_parameter_list_input,v,assertArray(v)",
            "input_parameter_set_input,v,assertSet(v)",
            "input_parameter_map_input,v,assertObject(v)",
            "input_parameter_enum_map,m,assertArray(m)",
            "input_parameter_any_map,m,assertArray(m)",
            "input_parameter_nullable_list_input,v,if (v != null) assertArray(v)",
            "input_parameter_gtv,g,assertAny(g)",
            "input_parameter_nullable_gtv,g,if (g != null) assertAny(g)",
            "input_parameter_multiple,'s, s2','assertString(s)\n\tassertString(s2)'"
    )
    fun parameterTypeTest(queryName: String, queryParams: String, assertFun: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[queryName])
        val k = JavascriptQuery(query)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("export function ${queryName.snakeToLowerCamelCase()}QueryObject($queryParams) {")
            contains(assertFun)
            contains("return { name: \"$queryName\"")
        }
    }

}
