package net.postchain.rell.codegen.typescript

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TypescriptQueryTest {
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
        val k = TypescriptQuery(q)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("inputParameterNargs: async function(): Promise<number>")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> kotlin: {1}")
    @CsvSource(
            "my_ns1.q1_in_namespace,myNs1Q1InNamespace,e: TestEnum",
            "my_ns1.q2_in_namespace,myNs1Q2InNamespace,s: MyNs1TestStruct2",
            "my_ns1.q3a_return_type_enum,myNs1Q3aReturnTypeEnum,e: MyNs1LocalTestEnum",
            "my_ns1.q3b_return_type_enum,myNs1Q3bReturnTypeEnum,'m: Map<TestEnum, ByteArray>'",
            "my_ns1.q4_return_type_list_struct,myNs1Q4ReturnTypeListStruct,'m: Map<ByteArray, MyNs1MyNs12TestStruct2>'",
            "my_ns1.q5_return_type_list_struct,myNs1Q5ReturnTypeListStruct,'v: List<MyNs1LocalTestStruct>'",
            "my_ns1.q6_return_type_list_struct,myNs1Q6ReturnTypeListStruct,''",
            "my_ns1.q7_return_type_enum_map,myNs1Q7ReturnTypeEnumMap,''",
            "my_ns1.q8_return_type_enum_map,myNs1Q8ReturnTypeEnumMap,''",
            "my_ns1.q9_return_type_any_map,myNs1Q9ReturnTypeAnyMap,''",
            "my_ns1.q10_return_type_any_map,myNs1Q10ReturnTypeAnyMap,''",
            "my_ns1.my_ns2.q2_in_namespace,myNs1MyNs2Q2InNamespace,''",
            "my_ns1.my_ns2.q_3_in_namespace,myNs1MyNs2Q3InNamespace,''"
    )
    fun namespaceTest(rellQualifiedName: String, typescriptQualifiedName: String, params: String) {
        val q = kotlin.test.assertNotNull(testModule.queries[rellQualifiedName])
        val k = TypescriptQuery(q)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("${typescriptQualifiedName}: async function")
            contains("gtxClient.query(\"$rellQualifiedName\"")
        }
    }

    @ParameterizedTest(name = "query for return type {0} should convert to {1}")
    @CsvSource(
            "return_type_enum,TestEnum",
            "return_type_boolean,boolean",
            "return_type_integer,number",
            "return_type_text,string",
            "return_type_decimal,number",
            "return_type_byte_array,Buffer",
            "return_type_pubkey,Buffer",
            "return_type_entity,number",
            "return_type_nullable_entity,'number | null'",
            "return_type_struct,TestStruct",
            "return_type_rowid,number",
            "return_type_nullable_rowid,'number | null'",
            "return_type_gtv,any",
            "return_type_nullable_gtv,'any | null'",
            "return_type_list_integer,number[]",
            "return_type_list_byte_array,Buffer[]",
            "return_type_set_integer,Set<number>",
            "return_type_list_struct,TestStruct[]",
            "return_type_list_entity,number[]",
            "return_type_list_gtv,any[]",
            "return_type_set_gtv,Set<any>",
            "return_type_map,{[x: string]: string}",
            "return_type_enum_map,{[x: TestEnum]: string}",
            "return_type_any_map,{[x: TestStruct]: string}",
            "return_type_unnamed_tuple,[number]", // Unnamed tuples are arrays with unknown entries
    )
    fun returnTypeTest(type: String, returnType: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[type])
        val k = TypescriptQuery(query)
        val formatted = k.format()
        assertThat(formatted.trim()).all {
            contains("Promise<$returnType>")
        }
    }

    @ParameterizedTest(name = "query {0} should contain params {1} with type-conversion {2}")
    @CsvSource(
            "input_parameter_nargs,'',''",
            "input_parameter_text,t: string,', {t}'",
            "input_parameter_nullable,t: string | null,', {t}'",
            "input_parameter_integer,i: number,', {i}'",
            "input_parameter_enum,e: TestEnum,', {e}'",
            "input_parameter_boolean,b: boolean,', {b}'",
            "input_parameter_rowid,r: number,', {r}'",
            "input_parameter_pubkey,pubkey: Buffer,', {pubkey}'",
            "input_parameter_blockchain_rid,blockchainRid: Buffer,', {blockchainRid}'",
            "input_parameter_entity,e: number,', {e}'",
            "input_parameter_struct,s: TestStruct,', {s}'",
            "input_parameter_list_input,v: Buffer[],', {v}'",
            "input_parameter_set_input,v: Set<Buffer>,', {Array.from(v)}'",
            "input_parameter_map_input,'v: {[x: string]: Buffer}',', {v}'",
            "input_parameter_enum_map,'m: {[x: TestEnum]: Buffer}',', {m}'",
            "input_parameter_any_map,'m: {[x: TestStruct]: Buffer}',', {m}'",
            "input_parameter_nullable_list_input,v: Buffer[] | null,', {v}'"
    )
    fun parameterTypeTest(queryName: String, funParams: String, queryParam: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[queryName])
        val k = TypescriptQuery(query)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("async function($funParams)")
            contains("gtxClient.query(\"$queryName\"$queryParam")
        }
    }

    @ParameterizedTest(name = "object creation")
    @CsvSource(
            "return_type_nullable_named_tuple,'foo: number',ReturnTypeNullableNamedTupleReturnType,' | null'",
            "return_type_named_tuple,'foo: number',ReturnTypeNamedTupleReturnType,''",
            "return_type_named_tuple_list,'rowid: number;a: boolean',ReturnTypeNamedTupleListReturnType,[]",
            "return_type_proposals_since,'rowid: number;a: boolean',ReturnTypeProposalsSinceReturnType,[]"
    )
    fun namedTupleCreatesObject(name: String, returnType: String, returnName: String, appendedType: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[name])
        val k = TypescriptQuery(query)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("type $returnName = {")
            returnType.split(";").forEach { contains(it) }
            contains("Promise<$returnName$appendedType>")
        }
    }
}
