package net.postchain.rell.codegen.kotlin

import assertk.all
import assertk.assertions.contains
import assertk.assertions.endsWith
import assertk.assertions.startsWith
import net.postchain.gtv.GtvFactory.gtv
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

    companion object {

        lateinit var testModule: R_Module
        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            testModule = RellCliUtils.compileApp(
                C_SourceDir.diskDir(File(this::class.java.getResource("queries.rell")!!.toURI()).parentFile),
                C_CompilerModuleSelection(
                    listOf(R_ModuleName.of("queries"))
                ),
                true,
                C_CompilerOptions.DEFAULT
            ).let {
                kotlin.test.assertNotNull(it.moduleMap[R_ModuleName.of("queries")])
            }
        }
    }

    @ParameterizedTest(name = "query for return type {0} should convert to {1}")
    @CsvSource(
        "boolean,asBoolean()",
        "integer,asInteger()",
        "text,asString()",
        "byte_array,asByteArray()",
        "entity,asInteger()",
        "decimal,asString()",
        "map,'asDict().mapValues { (k, v) -> v.asString() }'",
        "list_integer,map{ it.asInteger() }",
        "proposals_since,gtv(since))))"
    )
    fun returnTypeTest(type: String, returnType: String) {
        val query = kotlin.test.assertNotNull(testModule.queries["get_$type"])
        val k = KotlinQuery(query, "")
        val formatted = k.format()
        assertk.assert(formatted).all {
            startsWith("fun PostchainClient.")
            contains("get${type.snakeToUpperCamelCase()}")
            endsWith(returnType)
        }
    }

    @ParameterizedTest(name = "query {0} should contain params {1} with gtv-conversion {2}")
    @CsvSource(
        "g_nargs,'',''",
        "g_text,t: String,\"t\" to gtv(t)",
        "g_integer,i: Long,\"i\" to gtv(i)",
        "g_boolean,b: Boolean,\"b\" to gtv(b)",
        "g_entity,e: Long,\"e\" to gtv(e)",
        "g_struct,s: TestStruct,\"s\" to s.toGtv()",
        "g_list_input,v: List<ByteArray>,\"v\" to gtv(v.map { gtv(it) })",
        "g_set_input,v: Set<ByteArray>,\"v\" to gtv(v.map { gtv(it) })",
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