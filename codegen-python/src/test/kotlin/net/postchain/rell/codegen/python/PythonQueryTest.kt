package net.postchain.rell.codegen.python

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PythonQueryTest {
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
        val k = PythonQuery(q)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("def input_parameter_nargs() -> QueryObject:")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> python: {1}")
    @CsvSource(
            "my_ns1.q1_in_namespace,my_ns1_q1_in_namespace,e: TestEnum",
            "my_ns1.q2_in_namespace,my_ns1_q2_in_namespace,s: MyNs1TestStruct2",
            "my_ns1.q3a_return_type_enum,my_ns1_q3a_return_type_enum,e: MyNs1LocalTestEnum",
            "my_ns1.q3b_return_type_enum,my_ns1_q3b_return_type_enum,'m: Dict[TestEnum, bytes]'",
            "my_ns1.q4_return_type_list_struct,my_ns1_q4_return_type_list_struct,'m: Dict[bytes, MyNs1MyNs12TestStruct2]'",
            "my_ns1.q5_return_type_list_struct,my_ns1_q5_return_type_list_struct,'v: List[MyNs1LocalTestStruct]'",
            "my_ns1.q6_return_type_list_struct,my_ns1_q6_return_type_list_struct,''",
            "my_ns1.q7_return_type_enum_map,my_ns1_q7_return_type_enum_map,''",
            "my_ns1.q8_return_type_enum_map,my_ns1_q8_return_type_enum_map,''",
            "my_ns1.q9_return_type_any_map,my_ns1_q9_return_type_any_map,''",
            "my_ns1.q10_return_type_any_map,my_ns1_q10_return_type_any_map,''",
            "my_ns1.my_ns2.q2_in_namespace,my_ns1_my_ns2_q2_in_namespace,''",
            "my_ns1.my_ns2.q_3_in_namespace,my_ns1_my_ns2_q_3_in_namespace,''"
    )
    fun namespaceTest(rellQualifiedName: String, pythonQualifiedName: String, params: String) {
        val q = kotlin.test.assertNotNull(testModule.queries[rellQualifiedName])
        val k = PythonQuery(q)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("def $pythonQualifiedName(")
            contains("$params) -> QueryObject:")
            contains("return QueryObject(name=\"$rellQualifiedName\"")
        }
    }

    @ParameterizedTest(name = "query for return type {0} should convert to {1}")
    @CsvSource(
            "return_type_enum,str",
            "return_type_boolean,int",
            "return_type_integer,int",
            "return_type_big_integer,int",
            "return_type_text,str",
            "return_type_decimal,str",
            "return_type_byte_array,bytes",
            "return_type_pubkey,bytes",
            "return_type_entity,int",
            "return_type_nullable_entity,'Optional[int]'",
            "return_type_struct,TestStruct",
            "return_type_rowid,int",
            "return_type_nullable_rowid,'Optional[int]'",
            "return_type_gtv,RawGtv",
            "return_type_nullable_gtv,RawGtv",
            "return_type_list_integer,List[int]",
            "return_type_list_boolean,List[int]",
            "return_type_list_byte_array,List[bytes]",
            "return_type_set_integer,List[int]",
            "return_type_list_struct,List[TestStruct]",
            "return_type_nullable_list_struct,Optional[List[TestStruct]]",
            "return_type_list_entity,List[int]",
            "return_type_nullable_list_entity,Optional[List[int]]",
            "return_type_list_gtv,List[RawGtv]",
            "return_type_list_list_list,List[List[List[RawGtv]]]",
            "return_type_set_gtv,List[RawGtv]",
            "return_type_map,'Dict[str, str]'",
            "return_type_nullable_map,'Optional[Dict[str, str]]'",
            "return_type_enum_map,'List[Tuple[TestEnum, str]]'",
            "return_type_nullable_enum_map,'Optional[List[Tuple[TestEnum, str]]]'",
            "return_type_any_map,'List[Tuple[TestStruct, str]]'",
            "return_type_unnamed_tuple,Tuple[int]",
    )
    fun returnTypeTest(type: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[type])
        val k = PythonQuery(query)
        val formatted = k.format()
        assertThat(formatted.trim()).all {
            // we always return raw QueryObject
            contains("-> QueryObject:")
        }
    }

    @ParameterizedTest(name = "query {0} should contain params {1} with type-conversion {2}")
    @CsvSource(
            "input_parameter_nargs,'',''",
            "input_parameter_text,t: str,'args=[t]'",
            "input_parameter_nullable,t: Optional[str],'args=[t]'",
            "input_parameter_integer,i: int,'args=[i]'",
            "input_parameter_big_integer,i: BigInt,'args=[i]'",
            "input_parameter_enum,e: TestEnum,'args=[e]'",
            "input_parameter_boolean,b: bool,'args=[b]'",
            "input_parameter_rowid,r: int,'args=[r]'",
            "input_parameter_pubkey,pubkey: bytes,'args=[pubkey]'",
            "input_parameter_blockchain_rid,blockchain_rid: bytes,'args=[blockchain_rid]'",
            "input_parameter_entity,e: int,'args=[e]'",
            "input_parameter_struct,s: TestStruct,'args=[s.to_gtv_values()]'",
            "input_parameter_list_input,v: List[bytes],'args=[v]'",
            "input_parameter_set_input,v: Set[bytes],'args=[list(v)]'",
            "input_parameter_map_input,'v: Dict[str, bytes]','args=[v]'",
            "input_parameter_enum_map,'m: Dict[TestEnum, bytes]','args=[m]'",
            "input_parameter_any_map,'m: Dict[TestStruct, bytes]','args=[m]'",
            "input_parameter_nullable_list_input,v: Optional[List[bytes]],'args=[v]'",
            "input_parameter_multiple, 's: str, s2: str','args=[s, s2]'",
            "input_parameter_gtv,g: RawGtv,'args=[g]'",
            "input_parameter_nullable_gtv,g: Optional[RawGtv],'args=[g]'",
    )
    fun parameterTypeTest(queryName: String, funParams: String, queryParam: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[queryName])
        val k = PythonQuery(query)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("def $queryName($funParams) -> QueryObject")
            if (queryParam.isEmpty()) {
                contains("QueryObject(name=\"$queryName\"")
            } else {
                contains("QueryObject(name=\"$queryName\"")
                contains("args={")
            }
        }
    }

    @ParameterizedTest(name = "object creation")
    @CsvSource(
        "return_type_nullable_named_tuple,return_type_nullable_named_tuple",
        "return_type_named_tuple,return_type_named_tuple",
        "return_type_named_tuple_list,return_type_named_tuple_list",
        "return_type_proposals_since,return_type_proposals_since"
    )
    fun namedTupleCreatesObject(name: String, returnName: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[name])
        val k = PythonQuery(query)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("def $name")
            contains("return QueryObject(name=\"$returnName\"")
        }
    }
}