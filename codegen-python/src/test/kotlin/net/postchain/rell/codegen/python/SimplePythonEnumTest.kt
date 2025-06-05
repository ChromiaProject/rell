package net.postchain.rell.codegen.python

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

class SimplePythonEnumTest {
    companion object : SingleFileRellApp("enumerations"){
        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @Test
    fun simpleEnumerations() {
        val enum = assertNotNull(testModule.enums["test_enum"], "enum does not exist")
        val formatted = PythonEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()

        assertThat(formatted).all {
            contains("class TestEnum(Enum):")
            contains("a")
            contains("b_value")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> typescript: {1}")
    @CsvSource(
        "my_ns1.ns_enum1,MyNs1NsEnum1",
        "my_ns1.my_ns2.ns_enum1,MyNs1MyNs2NsEnum1",
        "my_ns1.my_ns2.ns_enum2,MyNs1MyNs2NsEnum2",
        "my_ns1.my_ns2.ns_enum_3,MyNs1MyNs2NsEnum3"
    )
    fun namespaceTest(rellQualifiedName: String, pythonQualifiedName: String) {
        val enum = assertNotNull(testModule.enums[rellQualifiedName])
        val formatted = PythonEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()
        assertThat(formatted).all {
            contains("class $pythonQualifiedName(Enum)")
        }
    }
}