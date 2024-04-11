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
            contains("export function inputParameterNargsQueryObject(): QueryObject<number>")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> typescript: {1}")
    @CsvSource(
            "my_ns1.q1_in_namespace,myNs1Q1InNamespace,e: TestEnum",
            "my_ns1.q2_in_namespace,myNs1Q2InNamespace,s: MyNs1TestStruct2",
            "my_ns1.q3a_return_type_enum,myNs1Q3aReturnTypeEnum,e: MyNs1LocalTestEnum",
            "my_ns1.q3b_return_type_enum,myNs1Q3bReturnTypeEnum,'m: {[x in TestEnum]: Buffer}'",
            "my_ns1.q4_return_type_list_struct,myNs1Q4ReturnTypeListStruct,'m: {[x in Buffer]: MyNs1MyNs12TestStruct2}'",
            "my_ns1.q5_return_type_list_struct,myNs1Q5ReturnTypeListStruct,'v: MyNs1LocalTestStruct[]'",
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
            contains("export function ${typescriptQualifiedName}QueryObject(")
            contains("$params): QueryObject<")
            contains("return { name: \"$rellQualifiedName\"")
        }
    }

    @ParameterizedTest(name = "query for return type {0} should convert to {1}")
    @CsvSource(
            "return_type_enum,TestEnum",
            "return_type_boolean,boolean",
            "return_type_integer,number",
            "return_type_big_integer,bigint",
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
            "return_type_set_integer,number[]",
            "return_type_list_struct,TestStruct[]",
            "return_type_nullable_list_struct,TestStruct[] | null",
            "return_type_list_entity,number[]",
            "return_type_nullable_list_entity,number[] | null",
            "return_type_list_gtv,any[]",
            "return_type_list_list_list,any[][][]",
            "return_type_set_gtv,any[]",
            "return_type_map,{[x in string]: string}",
            "return_type_nullable_map,{[x in string]: string} | null",
            "return_type_enum_map,{[x in TestEnum]: string}",
            "return_type_nullable_enum_map,{[x in TestEnum]: string} | null",
            "return_type_any_map,{[x in TestStruct]: string}",
            "return_type_unnamed_tuple,[number]", // Unnamed tuples are arrays with unknown entries
    )
    fun returnTypeTest(type: String, returnType: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[type])
        val k = TypescriptQuery(query)
        val formatted = k.format()
        assertThat(formatted.trim()).all {
            contains("QueryObject<$returnType>")
        }
    }

    @ParameterizedTest(name = "query {0} should contain params {1} with type-conversion {2}")
    @CsvSource(
            "input_parameter_nargs,'',undefined",
            "input_parameter_text,t: string,'{ t: t }'",
            "input_parameter_nullable,t: string | null,'{ t: t }'",
            "input_parameter_integer,i: number,'{ i: i }'",
            "input_parameter_big_integer,i: bigint,'{ i: i }'",
            "input_parameter_enum,e: TestEnum,'{ e: e }'",
            "input_parameter_boolean,b: boolean,'{ b: b }'",
            "input_parameter_rowid,r: number,'{ r: r }'",
            "input_parameter_pubkey,pubkey: Buffer,'{ pubkey: pubkey }'",
            "input_parameter_blockchain_rid,blockchainRid: Buffer,'{ blockchain_rid: blockchainRid }'",
            "input_parameter_entity,e: number,'{ e: e }'",
            "input_parameter_struct,s: TestStruct,'{ s: s }'",
            "input_parameter_list_input,v: Buffer[],'{ v: v }'",
            "input_parameter_set_input,v: Set<Buffer>,'{ v: Array.from(v) }'",
            "input_parameter_map_input,'v: {[x in string]: Buffer}','{ v: v }'",
            "input_parameter_enum_map,'m: {[x in TestEnum]: Buffer}','{ m: m }'",
            "input_parameter_any_map,'m: {[x in TestStruct]: Buffer}','{ m: m }'",
            "input_parameter_nullable_list_input,v: Buffer[] | null,'{ v: v }'",
            "input_parameter_multiple, 's: string,\n\ts2: string','{ s: s, s2: s2 }'",
            "input_parameter_gtv,g: any,'{ g: g }'",
            "input_parameter_nullable_gtv,g: any | null,'{ g: g }'",
    )
    fun parameterTypeTest(queryName: String, funParams: String, queryParam: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[queryName])
        val k = TypescriptQuery(query)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("($funParams)")
            contains("{ name: \"$queryName\", args: $queryParam }")
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
            contains("QueryObject<$returnName$appendedType>")
        }
    }
}
