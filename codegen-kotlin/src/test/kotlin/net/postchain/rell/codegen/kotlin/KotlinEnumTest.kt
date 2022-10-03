package net.postchain.rell.codegen.kotlin

import assertk.all
import org.junit.jupiter.api.Test

import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.BeforeAll
import kotlin.test.assertNotNull

internal class KotlinEnumTest {

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
        val formatted = KotlinEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()
        assertThat(formatted).all {
            contains("enum class TestEnum")
            contains("a,")
            contains("b_value")
        }
    }
}
