package net.postchain.rell.codegen.kotlin

import assertk.assertThat
import assertk.all
import assertk.assertions.contains
import net.postchain.gtv.GtvNull
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class KotlinQueryTest {

    companion object : SingleFileRellApp("queries") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @Test
    fun basicSyntaxTest() {
        val q = kotlin.test.assertNotNull(testModule.queries["input_parameter_nargs"])
        val k = KotlinQuery(q)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("fun PostchainQuery.inputParameterNargs() =")
            contains("querySync(\"input_parameter_nargs\"")
        }
    }
    @ParameterizedTest(name = "query for return type {0} should convert to {1}")
    @CsvSource(
        "return_type_enum,.let { TestEnum.valueOf(it.asString()) }",
        "return_type_boolean,.asBoolean()",
        "return_type_integer,.asInteger()",
        "return_type_text,.asString()",
        "return_type_decimal,.let { BigDecimal(it.asString()) }",
        "return_type_byte_array,asByteArray()",
        "return_type_entity,.let { RowId(it.asInteger()) }",
        "return_type_nullable_entity,.let { v -> if (v is GtvNull) null else v.let { RowId(it.asInteger()) } }",
        "return_type_struct,.toObject<TestStruct>()",
        "return_type_rowid,.let { RowId(it.asInteger()) }",
        "return_type_nullable_rowid,.let { v -> if (v is GtvNull) null else v.let { RowId(it.asInteger()) } }",
        "return_type_list_integer,.asArray().map { v -> v.asInteger() }",
        "return_type_set_integer,.asArray().map { v -> v.asInteger() }.toSet()",
        "return_type_list_struct,.asArray().map { v -> v.toObject<TestStruct>() }",
        "return_type_list_entity,.asArray().map { v -> v.let { RowId(it.asInteger()) } }",
        "return_type_map,'asDict().mapValues { (k, v) -> v.asString() }'",
        "return_type_enum_map,'asDict().mapValues { (k, v) -> k.let { TestEnum.valueOf(it.asString()) } to v.asString() }'",
        "return_type_unnamed_tuple,.asArray()", // Unnamed tuples are arrays with unknown entries
    )
    fun returnTypeTest(type: String, returnType: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[type])
        val k = KotlinQuery(query)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("fun PostchainQuery.")
            contains(type.snakeToLowerCamelCase())
            contains(returnType)
        }
    }

    @ParameterizedTest(name = "query {0} should contain params {1} with gtv-conversion {2}")
    @CsvSource(
        "input_parameter_nargs,'',''",
        "input_parameter_text,t: String,\"t\" to gtv(t)",
        "input_parameter_nullable,t: String?,\"t\" to t.let { if (it == null) GtvNull else gtv(it) }",
        "input_parameter_integer,i: Long,\"i\" to gtv(i)",
        "input_parameter_enum,e: TestEnum,\"e\" to gtv(e.ordinal.toLong()))",
        "input_parameter_boolean,b: Boolean,\"b\" to gtv(b)",
        "input_parameter_rowid,r: RowId,\"r\" to gtv(r.id)",
        "input_parameter_entity,e: RowId,\"e\" to gtv(e.id)",
        "input_parameter_struct,s: TestStruct,\"s\" to GtvObjectMapper.toGtvArray(s)",
        "input_parameter_list_input,v: List<WrappedByteArray>,\"v\" to gtv(v.map { gtv(it) })",
        "input_parameter_set_input,v: Set<WrappedByteArray>,\"v\" to gtv(v.map { gtv(it) })",
        "input_parameter_map_input,'v: Map<String, WrappedByteArray>',\"v\" to gtv(v.mapValues { gtv(it.value) })",
        "input_parameter_enum_map,'m: Map<TestEnum, WrappedByteArray>','\"m\" to gtv(m.map { (k, v) -> k.name to gtv(v) }.toMap())'",
    )
    fun parameterTypeTest(queryName: String, params: String, gtvParam: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[queryName])
        val k = KotlinQuery(query)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("($params) =")
            contains("\"$queryName\", gtv(mapOf($gtvParam")
        }
    }

    @ParameterizedTest(name = "query has imported")
    @CsvSource(
        "return_type_nullable_entity",
    )
    fun nullable(s: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[s])
        val k = KotlinQuery(query)
        assertThat(k.imports).contains("import ${GtvNull::class.qualifiedName}")
    }

    @ParameterizedTest(name = "object creation")
    @CsvSource(
        "return_type_named_tuple",
        "return_type_nullable_named_tuple",
        "return_type_named_tuple_list",
    )
    fun namedTupleCreatesObject(name: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[name])
        val k = KotlinQuery(query)
        val formatted = k.format()
        assertThat(k.imports).all {
            contains("import net.postchain.gtv.mapper.Name")
            contains("import net.postchain.gtv.Gtv")
        }
        assertThat(formatted).all {
            contains(".toObject<${name.snakeToUpperCamelCase()}Result>()")
            contains("data class ${name.snakeToUpperCamelCase()}Result")
        }
    }
}
