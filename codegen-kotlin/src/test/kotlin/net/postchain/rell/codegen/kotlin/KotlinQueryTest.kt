package net.postchain.rell.codegen.kotlin

import assertk.all
import assertk.assertions.contains
import assertk.assertions.endsWith
import assertk.assertions.startsWith
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.R_Module
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.RellCliUtils
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

internal class KotlinQueryTest {

    companion object : SingleFileRellApp("queries") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @ParameterizedTest(name = "query for return type {0} should convert to {1}")
    @CsvSource(
        "return_type_boolean,asBoolean()",
        "return_type_integer,asInteger()",
        "return_type_text,asString()",
        "return_type_decimal,asString()",
        "return_type_byte_array,asByteArray()",
        "return_type_entity,asInteger()",
        "return_type_struct,.let { TestStruct.fromGtv(it as GtvArray) }",
        "return_type_rowid,asInteger()",
        "return_type_list_integer,asArray().map{ it.asInteger() }",
        "return_type_map,'asDict().mapValues { (k, v) -> v.asString() }'",
        "return_type_proposals_since,gtv(since))))" // Custom structs not supported!
    )
    fun returnTypeTest(type: String, returnType: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[type])
        val k = KotlinQuery(query, "")
        val formatted = k.format()
        assertk.assert(formatted).all {
            contains("fun PostchainClient.")
            contains(type.snakeToLowerCamelCase())
            endsWith(returnType)
        }
    }

    @ParameterizedTest(name = "query {0} should contain params {1} with gtv-conversion {2}")
    @CsvSource(
        "input_parameter_nargs,'',''",
        "input_parameter_text,t: String,\"t\" to gtv(t)",
        "input_parameter_integer,i: Long,\"i\" to gtv(i)",
        "input_parameter_boolean,b: Boolean,\"b\" to gtv(b)",
        "input_parameter_entity,e: Long,\"e\" to gtv(e)",
        "input_parameter_struct,s: TestStruct,\"s\" to s.toGtv()",
        "input_parameter_list_input,v: List<ByteArray>,\"v\" to gtv(v.map { gtv(it) })",
        "input_parameter_set_input,v: Set<ByteArray>,\"v\" to gtv(v.map { gtv(it) })",
    )
    fun parameterTypeTest(queryName: String, params: String, gtvParam: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[queryName])
        val k = KotlinQuery(query, "")
        val formatted = k.format()
        assertk.assert(formatted).all {
            contains("($params) =")
            contains("\"$queryName\", gtv(mapOf($gtvParam")
        }
    }
}