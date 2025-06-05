package net.postchain.rell.codegen.python

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PythonOperationTest {

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
        val k = PythonOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("def input_parameter_text_operation(t: str) -> Operation:")
            contains("return Operation(op_name=\"input_parameter_text\", args=[t])")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> Python: {1}")
    @CsvSource(
        "my_ns1.op1_in_namespace,my_ns1_op1_in_namespace_operation",
        "my_ns1.my_ns2.op2_in_namespace,my_ns1_my_ns2_op2_in_namespace_operation",
        "my_ns1.my_ns2.op_3_in_namespace,my_ns1_my_ns2_op_3_in_namespace_operation"
    )
    fun namespaceTest(rellQualifiedOpName: String, pythonQualifiedOpName: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[rellQualifiedOpName])
        val k = PythonOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("return Operation(op_name=\"${pythonQualifiedOpName.substringBeforeLast("_operation")}\", args=[])")
        }
    }

    @ParameterizedTest(name = "operation {0} should contain params {1} with type-conversion {2}")
    @CsvSource(
        "input_parameter_nargs,'',''",
        "input_parameter_text,t: str,t",
        "input_parameter_integer,i: int,i",
        "input_parameter_big_integer,i: BigInt,i",
        "input_parameter_byte_array,b: bytes,b",
        "input_parameter_enum,e: TestEnum,e",
        "input_parameter_boolean,b: bool,b",
        "input_parameter_rowid,r: int,r",
        "input_parameter_pubkey,pubkey: bytes,pubkey",
        "input_parameter_nullable_pubkey,pubkey: Optional[bytes],pubkey",
        "input_parameter_blockchain_rid,blockchain_rid: bytes,blockchain_rid",
        "input_parameter_nullable_blockchain_rid,blockchain_rid: Optional[bytes],blockchain_rid",
        "input_parameter_gtv,g: RawGtv,g",
        "input_parameter_nullable_gtv,g: Optional[RawGtv],g",
        "input_parameter_list_gtv,g: List[RawGtv],g",
        "input_parameter_list_string,l: List[str],l",
        "input_parameter_set_gtv,g: Set[RawGtv],g",
        "input_parameter_entity,e: int,e",
        "input_parameter_struct,s: TestStruct,s",
        "input_parameter_list_input,v: List[bytes],v",
        "input_parameter_nullable_list_input,v: Optional[List[bytes]],v",
        "input_parameter_set_input,v: Set[bytes],v",
        "input_parameter_set_string,s: Set[str],s",
        "input_parameter_map_text_bytearray,'m: Dict[str, bytes]',m",
        "input_parameter_map_text_gtv,'m: Dict[str, RawGtv]',m",
        "input_parameter_map_integer_text,'m: Dict[int, str]',m",
        "input_parameter_map_gtv_text,'m: Dict[RawGtv, str]',m",
        "input_parameter_map_gtv_gtv,'m: Dict[RawGtv, RawGtv]',m",
        "input_parameter_map_enum_text,'m: Dict[TestEnum, str]',m",
        "input_parameter_multi,'s: str, s2: str','s, s2'"
    )
    fun parameterTypeTest(opName: String, params: String, gtvParam: String) {
        val op = kotlin.test.assertNotNull(testModule.operations[opName])
        val k = PythonOperation(op)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("def ${opName}_operation($params) -> Operation:")
            if (gtvParam.isEmpty()) {
                contains("return Operation(op_name=\"$opName\", args=[])")
            } else {
                contains("return Operation(op_name=\"$opName\", args=[$gtvParam])")
            }
        }
    }
}