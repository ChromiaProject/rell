package net.postchain.rell.codegen.kotlin

import assertk.all
import org.junit.jupiter.api.Test

import assertk.assert
import assertk.assertions.contains
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
        val struct = assertNotNull(testModule.enums["test_enum"], "enum does not exist")
        val formatted = KotlinEnumeration(struct).format()
        assert(formatted).all {
            contains("enum class TestEnum")
            contains("a,")
            contains("b_value")
        }
    }
}
