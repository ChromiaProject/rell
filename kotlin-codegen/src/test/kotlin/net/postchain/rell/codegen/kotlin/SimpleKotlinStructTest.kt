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

internal class SimpleKotlinStructTest {

    companion object {

        lateinit var testModule: R_Module
        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            testModule = RellCliUtils.compileApp(
                C_SourceDir.diskDir(File(this::class.java.getResource("structures.rell")!!.toURI()).parentFile),
                C_CompilerModuleSelection(
                    listOf(R_ModuleName.of("structures"))
                ),
                true,
                C_CompilerOptions.DEFAULT
            ).let {
                assertNotNull(it.moduleMap[R_ModuleName.of("structures")])
            }
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
        "map,Map<String, Long>",
        "nullable,String?"
    )
    fun simpleStructures(rellType: String, kotlinType: String) {
        val struct = assertNotNull(testModule.structs["${rellType}_struct"], "struct does not exist")
        val formatted = KotlinStruct(struct).format()
        assert(formatted).all {
            contains("@Name(\"a\") val a: $kotlinType")
        }
    }

    @Test
    fun jsonNotSupported() {
        val struct = assertNotNull(testModule.structs["json_struct"], "struct does not exist")
        assertThrows<IllegalArgumentException> {
            KotlinStruct(struct).format()
        }
    }


    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
        "name,String",
        "pubkey,ByteArray",
    )
    fun builtinTypes(keyword: String, kotlinType: String) {
        val struct = assertNotNull(testModule.structs["builtin_${keyword}"], "struct does not exist")
        val formatted = KotlinStruct(struct).format()
        assert(formatted).all {
            contains("@Name(\"$keyword\") val $keyword: $kotlinType")
        }
    }

    @Test
    fun nested() {
        val struct = assertNotNull(testModule.structs["nested_struct"])
        val k = KotlinStruct(struct)
        val formatted = k.format()
        assert(formatted).all {
            contains("@Name(\"a\") val a: TextStruct")
        }
    }
}