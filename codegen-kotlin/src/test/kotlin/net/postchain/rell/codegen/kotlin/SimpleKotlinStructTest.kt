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
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.model.R_Module
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertNotNull

internal class SimpleKotlinStructTest {

    companion object : SingleFileRellApp("structures") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
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
        "nullable,String?",
        "json,String",
    )
    fun simpleStructures(rellType: String, kotlinType: String): String {
        val struct = assertNotNull(testModule.structs["${rellType}_struct"], "struct does not exist")
        val formatted = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assert(formatted).all {
            contains("val a: $kotlinType")
        }
        return formatted
    }

    @Test
    fun nullableAnnotation() {
        val formatted = simpleStructures("nullable", "String?")
        assert(formatted).contains("@Nullable")
    }

    @ParameterizedTest(name = "builtin {0} becomes {1}")
    @CsvSource(
        "name,String",
        "pubkey,ByteArray",
    )
    fun builtinTypes(keyword: String, kotlinType: String) {
        val struct = assertNotNull(testModule.structs["builtin_${keyword}"], "struct does not exist")
        val formatted = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assert(formatted).all {
            contains("val $keyword: $kotlinType")
        }
    }

    @Test
    fun nested() {
        val struct = assertNotNull(testModule.structs["nested_struct"])
        val k = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct)
        val formatted = k.format()
        assert(formatted).all {
            contains("val a: TextStruct")
        }
    }
}
