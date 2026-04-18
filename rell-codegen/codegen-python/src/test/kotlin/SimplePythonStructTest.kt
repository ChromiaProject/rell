/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertNotNull

class SimplePythonStructTest {

    companion object : SingleFileRellApp("structures") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @ParameterizedTest(name = "rell type {0} becomes {1}")
    @CsvSource(
        "boolean,'a: bool'",
        "integer,'a: int'",
        "big_integer,'a: BigInt'",
        "decimal,'a: float'",
        "text,'a: str'",
        "byte_array,'a: bytes'",
        "rowid,'a: int'",
        "entity,'a: int'",
        "map,'a: Dict[str, int]'",
        "nullable,'a: Optional[str]'",
        "json,'a: str'",
        "list,'a: List[str]'",
        "set,'a: Set[str]'",
        "multi,'\n\ta: str\n\ti: int'",
    )
    fun simpleStructures(rellType: String, typescriptType: String) {
        format(rellType, typescriptType)
    }

    @ParameterizedTest(name = "rell: {0} -> Typescript: {1}")
    @CsvSource(
        "my_ns1.ns_struct1,MyNs1NsStruct1",
        "my_ns1.my_ns2.ns_struct2,MyNs1MyNs2NsStruct2",
        "my_ns1.my_ns2.ns_struct_3,MyNs1MyNs2NsStruct3"
    )
    fun namespaceTest(rellQualifiedName: String, pythonQualifiedName: String) {
        val struct = assertNotNull(testModule.structs[rellQualifiedName], "struct does not exist")
        val formatted = PythonStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains("@dataclass")
            contains("class $pythonQualifiedName:")
            contains("name: str")

            // default class methods for serialization/deserialization
            contains("def to_dict(self) -> Dict[str, Any]:")
            contains("return {\"name\": self.name}")
        }
    }

    @Test
    fun nullableAnnotation() {
        val formatted = format("nullable", "str")
        assertThat(formatted).contains("Optional[str]")
    }

    private fun format(rellType: String, pythonType: String): String {
        val struct = assertNotNull(testModule.structs["${rellType}_struct"], "struct does not exist")
        val formatted = PythonStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains(pythonType)
        }
        return formatted
    }

    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
        "name,str",
        "pubkey,bytes",
        "blockchain_rid,bytes",
        "transaction, int",
        "block, int",
        "block_struct, Block",
        "transaction_struct, Transaction",
    )
    fun builtinTypes(keyword: String, pythonType: String) {
        val struct = assertNotNull(testModule.structs["builtin_${keyword}"], "struct does not exist")
        val formatted = PythonStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).all {
            contains("$keyword: $pythonType")

        }
    }

    @Test
    fun nested() {
        val struct = assertNotNull(testModule.structs["nested_struct"])
        val k = PythonStruct(CamelCaseClassName.fromRellDefinition(struct), struct)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("a: TextStruct")
        }
    }
}