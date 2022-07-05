package net.postchain.rell.codegen.kotlin

import assertk.all
import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.RellCliUtils
import org.junit.jupiter.api.Test

import java.io.File

import assertk.assert
import assertk.assertions.contains
import net.postchain.rell.model.R_Module
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

internal class SimpleKotlinEntityTest {

    companion object {

        lateinit var testModule: R_Module
        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            testModule = RellCliUtils.compileApp(
                C_SourceDir.diskDir(File(this::class.java.getResource("entities.rell")!!.toURI()).parentFile),
                C_CompilerModuleSelection(
                    listOf(R_ModuleName.of("entities"))
                ),
                true,
                C_CompilerOptions.DEFAULT
            ).let {
                assertNotNull(it.moduleMap[R_ModuleName.of("entities")])
            }
        }


    }

    @Test
    fun format() {
        val entity = assertNotNull(testModule.entities["test_entity"])
        val k = KotlinEntity(entity)
        val formatted = k.format()
        assert(formatted).all {
            contains("TestEntity")
            contains("@Name(\"name\") val name: String")
            contains("num: Long")
            contains("bType: Boolean")
        }
    }

    @Test
    fun text() {
        val entity = assertNotNull(testModule.entities["multi_text_entity"])
        val k = KotlinEntity(entity)
        val formatted = k.format()
        assert(formatted).all {
            contains("@Name(\"name\") val name: String")
            contains("@Name(\"a\") val a: String")
            contains("@Name(\"b\") val b: String")
        }
    }

    @ParameterizedTest(name = "rell type {0} becomes {1}")
    @CsvSource(
        "boolean,Boolean",
        "integer,Long",
        "decimal,BigDecimal",
        "text,String",
        "byte_array,ByteArray",
        "rowid,Long",
    )
    fun simpleEntities(rellType: String, kotlinType: String) {
        val entity = assertNotNull(testModule.entities["${rellType}_entity"], "entity does not exist")
        val formatted = KotlinEntity(entity).format()
        assert(formatted).all {
            contains("@Name(\"a\") val a: $kotlinType")
        }
    }

    @Test
    fun jsonNotSupported() {
        val entity = assertNotNull(testModule.entities["json_entity"], "entity does not exist")
        assertThrows<IllegalArgumentException> {
            KotlinEntity(entity).format()
        }
    }


    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
        "name,String",
        "pubkey,ByteArray",
    )
    fun builtinTypes(keyword: String, kotlinType: String) {
        val entity = assertNotNull(testModule.entities["builtin_${keyword}"], "entity does not exist")
        val formatted = KotlinEntity(entity).format()
        assert(formatted).all {
            contains("@Name(\"$keyword\") val $keyword: $kotlinType")
        }
    }

}