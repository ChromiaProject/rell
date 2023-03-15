package net.postchain.rell.codegen.kotlin

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.endsWith
import net.postchain.gtv.GtvNull
import net.postchain.rell.codegen.SingleFileRellApp
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
            contains("query(\"input_parameter_nargs\"")
        }
    }

    @ParameterizedTest(name = "rell: {0} -> kotlin: {1}")
    @CsvSource(
            "my_ns1.q1_in_namespace,myNs1Q1InNamespace,e: TestEnum,).toObject<TestStruct>()",
            "my_ns1.q2_in_namespace,myNs1Q2InNamespace,s: MyNs1TestStruct2,).toObject<MyNs1TestStruct2>()",
            "my_ns1.q3a_return_type_enum,myNs1Q3aReturnTypeEnum,e: MyNs1LocalTestEnum,).let { TestEnum.values()[it.asInteger().toInt()] }",
            "my_ns1.q3b_return_type_enum,myNs1Q3bReturnTypeEnum,'m: Map<TestEnum, ByteArray>',).let { MyNs1LocalTestEnum.values()[it.asInteger().toInt()] }",
            "my_ns1.q4_return_type_list_struct,myNs1Q4ReturnTypeListStruct,'m: Map<ByteArray, MyNs1MyNs12TestStruct2>',).asArray().map { v -> v.toObject<TestStruct>() }",
            "my_ns1.q5_return_type_list_struct,myNs1Q5ReturnTypeListStruct,'v: List<MyNs1LocalTestStruct>',).asArray().map { v -> v.toObject<MyNs1LocalTestStruct>() }",
            "my_ns1.q6_return_type_list_struct,myNs1Q6ReturnTypeListStruct,'',).asArray().map { v -> v.toObject<MyNs1MyNs12TestStruct2>() }",
            "my_ns1.q7_return_type_enum_map,myNs1Q7ReturnTypeEnumMap,'',).asArray().map { pair -> pair.asArray().let { it[0].let { TestEnum.values()[it.asInteger().toInt()] } to it[1].asString() } }",
            "my_ns1.q8_return_type_enum_map,myNs1Q8ReturnTypeEnumMap,'',).asArray().map { pair -> pair.asArray().let { it[0].let { MyNs1LocalTestEnum.values()[it.asInteger().toInt()] } to it[1].asString() } }",
            "my_ns1.q9_return_type_any_map,myNs1Q9ReturnTypeAnyMap,'',).asArray().map { pair -> pair.asArray().let { it[0].toObject<TestStruct>() to it[1].asString() } }",
            "my_ns1.q10_return_type_any_map,myNs1Q10ReturnTypeAnyMap,'',).asArray().map { pair -> pair.asArray().let { it[0].toObject<MyNs1LocalTestStruct>() to it[1].asString() } }",
            "my_ns1.my_ns2.q2_in_namespace,myNs1MyNs2Q2InNamespace,'',).asString()",
            "my_ns1.my_ns2.q_3_in_namespace,myNs1MyNs2Q3InNamespace,'',).toObject<MyNs1MyNs2Q3InNamespaceResult>()"
    )
    fun namespaceTest(rellQualifiedName: String, kotlinQualifiedName: String, params: String, retValue: String) {
        val q = kotlin.test.assertNotNull(testModule.queries[rellQualifiedName])
        val k = KotlinQuery(q)
        val formatted = k.format()
        assertThat(formatted).all {
            contains("fun PostchainQuery.$kotlinQualifiedName($params) =")
            contains("query(\"$rellQualifiedName\"")
            contains(retValue)
        }
    }

    @ParameterizedTest(name = "query for return type {0} should convert to {1}")
    @CsvSource(
            "return_type_enum,).let { TestEnum.values()[it.asInteger().toInt()] }",
            "return_type_boolean,).asBoolean()",
            "return_type_integer,).asInteger()",
            "return_type_text,).asString()",
            "return_type_decimal,).let { BigDecimal(it.asString()) }",
            "return_type_byte_array,).asByteArray() ",
            "return_type_pubkey,).asByteArray() ",
            "return_type_entity,).let { RowId(it.asInteger()) }",
            "return_type_nullable_entity,).let { v -> if (v is GtvNull) null else v.let { RowId(it.asInteger()) } }",
            "return_type_struct,).toObject<TestStruct>()",
            "return_type_rowid,).let { RowId(it.asInteger()) }",
            "return_type_nullable_rowid,).let { v -> if (v is GtvNull) null else v.let { RowId(it.asInteger()) } }",
            "return_type_gtv,)",
            "return_type_nullable_gtv,).let { v -> if (v is GtvNull) null else v }",
            "return_type_list_integer,).asArray().map { v -> v.asInteger() }",
            "return_type_list_byte_array,).asArray().map { v -> v.asByteArray() }",
            "return_type_set_integer,).asArray().map { v -> v.asInteger() }.toSet()",
            "return_type_list_struct,).asArray().map { v -> v.toObject<TestStruct>() }",
            "return_type_list_entity,).asArray().map { v -> v.let { RowId(it.asInteger()) } }",
            "return_type_list_gtv,).asArray()",
            "return_type_set_gtv,).asArray().toSet()",
            "return_type_map,').asDict().mapValues { (_, v) -> v.asString() }'",
            "return_type_enum_map,').asArray().map { pair -> pair.asArray().let { it[0].let { TestEnum.values()[it.asInteger().toInt()] } to it[1].asString() } }'",
            "return_type_any_map,').asArray().map { pair -> pair.asArray().let { it[0].toObject<TestStruct>() to it[1].asString() } }'",
            "return_type_unnamed_tuple,).asArray()", // Unnamed tuples are arrays with unknown entries
    )
    fun returnTypeTest(type: String, returnType: String) {
        val query = kotlin.test.assertNotNull(testModule.queries[type])
        val k = KotlinQuery(query)
        val formatted = k.format()
        assertThat(formatted.trim()).all {
            contains("fun PostchainQuery.")
            contains(type.snakeToLowerCamelCase())
            endsWith(returnType)
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
            "input_parameter_pubkey,pubkey: PubKey,\"pubkey\" to gtv(pubkey.data)",
            "input_parameter_blockchain_rid,blockchainRid: BlockchainRid,\"blockchain_rid\" to gtv(blockchainRid)",
            "input_parameter_entity,e: RowId,\"e\" to gtv(e.id)",
            "input_parameter_struct,s: TestStruct,\"s\" to GtvObjectMapper.toGtvArray(s)",
            "input_parameter_list_input,v: List<ByteArray>,\"v\" to gtv(v.map { gtv(it) })",
            "input_parameter_set_input,v: Set<ByteArray>,\"v\" to gtv(v.map { gtv(it) })",
            "input_parameter_map_input,'v: Map<String, ByteArray>',\"v\" to gtv(v.mapValues { gtv(it.value) })",
            "input_parameter_enum_map,'m: Map<TestEnum, ByteArray>','\"m\" to gtv(m.map { (k, v) -> gtv(gtv(k.ordinal.toLong()), gtv(v)) })'",
            "input_parameter_any_map,'m: Map<TestStruct, ByteArray>','\"m\" to gtv(m.map { (k, v) -> gtv(GtvObjectMapper.toGtvArray(k), gtv(v)) })'",
            "input_parameter_nullable_list_input,v: List<ByteArray>?,'\"v\" to v.let { if (it == null) GtvNull else gtv(it.map { gtv(it) }) })'",
            "input_parameter_multiple, 's: String,\n\ts2: String', '\"s\" to gtv(s), \"s2\" to gtv(s2)'",
            "input_parameter_gtv,g: Gtv,'\"g\" to g'",
            "input_parameter_nullable_gtv,g: Gtv?,'\"g\" to g.let { if (it == null) GtvNull else it })'",
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
            "return_type_proposals_since,"
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
