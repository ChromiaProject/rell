/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.lang.module.ExternalModuleTest
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import org.junit.Test

class GlobalConstantTest: BaseRellTest() {
    @Test fun testSimplest() {
        def("val X = 123;")
        chk("X", "int[123]")
        chk("_type_of(X)", "text[integer]")
    }

    @Test fun testDefinitionSyntax() {
        chkCompile("val X = 123;", "OK")
        chkCompile("val X: integer = 123;", "OK")
        chkCompile("val X: text = 123;", "ct_err:def:const_expr_type:[text]:[integer]")
        chkCompile("val X;", "ct_err:syntax")
        chkCompile("val X: integer;", "ct_err:syntax")
        chkCompile("val _ = 123;", "ct_err:def:const:wildcard")
        chkCompile("val _: integer = 123;", "ct_err:def:const:wildcard")
        chkCompile("val (X, Y) = (123, 456);", "ct_err:syntax")
        chkCompile("val (X: integer, Y: integer) = (123, 456);", "ct_err:syntax")
    }

    @Test fun testDefinitionOrder() {
        def("val X = 2;")
        def("val Y = Z * 3;")
        def("val Z = X * 4;")

        chk("X", "int[2]")
        chk("Y", "int[24]")
        chk("Z", "int[8]")
    }

    @Test fun testDefinitionInterModule() {
        file("x.rell", "module; val X = 2;")
        file("y.rell", "module; import z; val Y = z.Z * 3;")
        file("z.rell", "module; import x; val Z = x.X * 4;")
        def("import x; import y; import z;")

        chk("x.X", "int[2]")
        chk("y.Y", "int[24]")
        chk("z.Z", "int[8]")
    }

    @Test fun testRecursionExplicitType() {
        chkCompile(
                "val X: integer = Y * 2; val Y: integer = X * 3;",
                "ct_err:[def:const:cycle:0::X:1::Y][def:const:cycle:1::Y:0::X]"
        )
    }

    @Test fun testRecursionImplicitType() {
        chkCompile("val X = Y * 2; val Y = X * 3;",
            "ct_err:[fn_type_recursion:CONSTANT:[Y]][fn_type_recursion:CONSTANT:[X]]")
    }

    @Test fun testRecursionInterModuleImplicitType() {
        file("a.rell", "module; import b; val X = b.Y;")
        file("b.rell", "module; import c; val Y = c.Z;")
        file("c.rell", "module; import a; val Z = a.X;")

        chkCompile("import a;", """ct_err:
            [a.rell:def:const:cycle:0:a:X:1:b:Y]
            [a.rell:fn_type_recursion:CONSTANT:[Y]]
            [b.rell:def:const:cycle:1:b:Y:2:c:Z]
            [b.rell:fn_type_recursion:CONSTANT:[Z]]
            [c.rell:def:const:cycle:2:c:Z:0:a:X]
            [c.rell:fn_type_recursion:CONSTANT:[X]]
        """)
    }

    @Test fun testRecursionInterModuleExplicitType() {
        file("a.rell", "module; import b; val X: integer = b.Y;")
        file("b.rell", "module; import c; val Y: integer = c.Z;")
        file("c.rell", "module; import a; val Z: integer = a.X;")

        chkCompile("import a;", """ct_err:
            [a.rell:def:const:cycle:0:a:X:1:b:Y]
            [b.rell:def:const:cycle:1:b:Y:2:c:Z]
            [c.rell:def:const:cycle:2:c:Z:0:a:X]
        """)
    }

    @Test fun testRecursionFunctionImplicitType(){
        def("val X = y();")
        def("function y() = Z;")
        def("val Z = X;")
        chkCompile("", """ct_err:
            [def:const:bad_expr:[0::X]:fn:y]
            [fn_type_recursion:FUNCTION:[y]]
            [fn_type_recursion:CONSTANT:[Z]]
            [fn_type_recursion:CONSTANT:[X]]
        """)
    }

    @Test fun testRecursionFunctionExplicitType(){
        def("val X: integer = y();")
        def("function y(): integer = Z;")
        def("val Z: integer = X;")
        chkCompile("", "ct_err:def:const:bad_expr:[0::X]:fn:y")
    }

    @Test fun testRuntimeError() {
        chkCompile("val X: integer = 123 / 0;", "ct_err:expr:/:div0:123")
        def("val Y: integer = abs(123 / 0);")
        chk("0", "rt_err:expr:/:div0:123")
    }

    @Test fun testCollectionTypeInference() {
        chkCompile("val X = [];", "ct_err:expr_list_no_type")
        chkCompile("val X: list<integer> = [];", "ct_err:def:const:bad_type:mutable:[0::X]:[list<integer>]")
    }

    @Test fun testNameConflictLocalVar() {
        def("val X = 123;")
        chkEx("{ val X = 456; return X; }", "int[456]")
    }

    @Test fun testNameConflictOuterNamespace() {
        def("""
            val X = 123;
            function f() = X;
            namespace a {
                val X = 456;
                function g() = X;
            }
        """)

        chk("X", "int[123]")
        chk("f()", "int[123]")
        chk("a.X", "int[456]")
        chk("a.g()", "int[456]")
    }

    @Test fun testNameConflictImportWild() {
        file("lib.rell", "module; val X = 456;")
        def("val X = 123;")
        def("import lib.*;")
        chk("X", "int[123]")
    }

    @Test fun testNameConflictImportExact() {
        file("lib.rell", "module; val X = 456;")
        chkCompile("val X = 123; import lib.{X};",
                "ct_err:[name_conflict:user:X:IMPORT:main.rell(1:26)][name_conflict:user:X:CONSTANT:main.rell(1:5)]")
        chkCompile("import lib.{X}; val X = 123;",
                "ct_err:[name_conflict:user:X:CONSTANT:main.rell(1:21)][name_conflict:user:X:IMPORT:main.rell(1:13)]")
    }

    @Test fun testCompileTimeConstant() {
        def("val X = 0;")
        def("val Y = 1;")
        def("val Z = 2;")

        def("val A = [1,2,3].size();")
        def("val B = C * 2;")
        def("val C = (X + Y + Z) / 2;")

        val t = "(123,'hello',true)"

        chk("_type_of($t[X])", "text[integer]")
        chk("_type_of($t[Y])", "text[text]")
        chk("_type_of($t[Z])", "text[boolean]")
        chk("_type_of($t[A])", "ct_err:expr_subscript:tuple:no_const")
        chk("_type_of($t[B])", "text[boolean]")
        chk("_type_of($t[C])", "text[text]")

        chk("$t[X]", "int[123]")
        chk("$t[Y]", "text[hello]")
        chk("$t[Z]", "boolean[true]")
        chk("$t[B]", "boolean[true]")
        chk("$t[C]", "text[hello]")
    }

    @Test fun testCompileTimeConstantRecursion() {
        chkCompile(
                "val X: integer = Y; val Y: integer = Z; val Z: integer = X;",
                "ct_err:[def:const:cycle:0::X:1::Y][def:const:cycle:1::Y:2::Z][def:const:cycle:2::Z:0::X]"
        )
    }

    @Test fun testCompileTimeConstantWhenConflict() {
        def("val X = 123;")
        def("val Y = 123;")
        chk("when (_nop(123)) { X -> 100; Y -> 200; else -> 300; }", "ct_err:when_expr_dupvalue:int[123]")
    }

    @Test fun testExternalModule() {
        tstCtx.useSql = true
        tstCtx.blockchain(100, "1234")
        tstCtx.blockchain(101, "5678")

        val t = RellCodeTester(tst.tstCtx)
        t.dropTables = true
        t.init()

        ExternalModuleTest.initExternalChain(tst, 100, listOf())
        ExternalModuleTest.initExternalChain(tst, 101, listOf())

        tst.dropTables = false
        tst.chainDependency("foo", "1234", 1)
        tst.chainDependency("bar", "5678", 1)

        file("ext.rell", "@external module; val X = 123;")
        def("import ext;")
        def("@external('foo') import foo: ext;")
        def("@external('bar') import bar: ext;")

        chk("ext.X", "int[123]")
        chk("foo.X", "int[123]")
        chk("bar.X", "int[123]")
    }

    @Test fun testSideEffects() {
        def("val X = _nop_print(100);")
        def("val Y = _nop_print(X + 50);")
        def("val Z = _nop_print(X + Y + 1000);")
        def("function f() = X + Y + Z;")

        chk("0", "int[0]")
        chkOut("100", "150", "1250")

        chk("X", "int[100]")
        chkOut("100", "150", "1250")

        chk("Y", "int[150]")
        chkOut("100", "150", "1250")

        chk("Z", "int[1250]")
        chkOut("100", "150", "1250")

        chk("f()", "int[1500]")
        chkOut("100", "150", "1250")
    }

    @Test fun testExprUserFunctionCall() {
        def("function f(x: integer, y: integer) = x + y;")
        def("function g(x: integer = 123, y: text = 'Hello') = x + y;")

        chkCompile("val X = f(123, 456);", "ct_err:def:const:bad_expr:[0::X]:fn:f")

        chkCompile("val X = g(123, 'Hello');", "ct_err:def:const:bad_expr:[0::X]:fn:g")

        chkCompile("val X = g();", """ct_err:
            [def:const:bad_expr:[0::X]:param_default_value]
            [def:const:bad_expr:[0::X]:param_default_value]
            [def:const:bad_expr:[0::X]:fn:g]
        """)

        chkCompile("val X = f(123, *);",
                "ct_err:[def:const:bad_type:not_pure:[0::X]:[(integer)->integer]][def:const:bad_expr:[0::X]:partial_call]")
    }

    @Test fun testExprModuleArgs() {
        def("struct module_args { x: integer; }")
        def("val A = chain_context.args;")
        def("val B = chain_context.args.x;")
        tst.moduleArgs("" to """[123]""")

        chk("A", "module_args[x=int[123]]")
        chk("B", "int[123]")

        chkCompile("val X = chain_context.raw_config;",
            "ct_err:def:const:bad_expr:[2::X]:fn:prop:[chain_context.raw_config]")
        chkCompile("val X = chain_context.blockchain_rid;",
            "ct_err:def:const:bad_expr:[2::X]:fn:prop:[chain_context.blockchain_rid]")
    }

    @Test fun testExprEntity() {
        def("entity user { name; }")
        def("entity role { user; }")
        def("struct s { u: user; }")
        def("struct p { m: (a:integer, b:(s,boolean)); }")
        def("function f() = user @ {};")

        chkCompile("val X = create user('bob');",
            "ct_err:[def:const:bad_type:not_pure:[0::X]:[user]][def:const:bad_expr:[0::X]:create]")

        chkCompile("val X = user.from_gtv(gtv.from_json(''));", """ct_err:
            [def:const:bad_type:not_pure:[0::X]:[user]]
            [def:const:bad_expr:[0::X]:fn:sys:[rell.gtv_ext(user).from_gtv]]
        """)

        chkCompile("val X = s.from_gtv(gtv.from_json(''));",
            "ct_err:[def:const:bad_type:not_pure:[0::X]:[s]][def:const:bad_expr:[0::X]:fn:sys:[rell.gtv_ext(s).from_gtv]]")

        chkCompile("val X = s.from_gtv_pretty(gtv.from_json(''));", """ct_err:
            [def:const:bad_type:not_pure:[0::X]:[s]]
            [def:const:bad_expr:[0::X]:fn:sys:[rell.gtv_ext(s).from_gtv_pretty]]
        """)

        chkCompile("val X = p.from_gtv_pretty(gtv.from_json(''));", """ct_err:
            [def:const:bad_type:not_pure:[0::X]:[p]]
            [def:const:bad_expr:[0::X]:fn:sys:[rell.gtv_ext(p).from_gtv_pretty]]
        """)

        chkCompile("val X = struct<user>.from_gtv(gtv.from_json(''));", "OK")
        chkCompile("val X = struct<mutable user>.from_gtv(gtv.from_json('')).name;", "OK")

        chkCompile("val X = struct<role>.from_gtv(gtv.from_json(''));", """ct_err:
            [def:const:bad_type:not_pure:[0::X]:[struct<role>]]
            [def:const:bad_expr:[0::X]:fn:sys:[rell.gtv_ext(struct<role>).from_gtv]]
        """)

        chkCompile("val X = struct<mutable role>.from_gtv(gtv.from_json(''));", """ct_err:
            [def:const:bad_type:mutable:[0::X]:[struct<mutable role>]]
            [def:const:bad_expr:[0::X]:fn:sys:[rell.gtv_ext(struct<mutable role>).from_gtv]]
        """)

        chkConstErr("struct<user>('Bob').to_mutable()",
            "ct_err:def:const:bad_type:mutable:[0::X]:[struct<mutable user>]")

        chkConst("struct<mutable user>('Bob').to_immutable()", "struct<user>[name=text[Bob]]")

        chkConstErr("struct<role>(user@{}).to_mutable()", """ct_err:
            [def:const:bad_type:mutable:[0::X]:[struct<mutable role>]]
            [def:const:bad_expr:[0::X]:fn:sys:[struct<role>.to_mutable]]
            [def:const:bad_expr:[0::X]:at_expr]
        """)

        chkConstErr("struct<mutable role>(user@{}).to_immutable()", """ct_err:
            [def:const:bad_type:not_pure:[0::X]:[struct<role>]]
            [def:const:bad_expr:[0::X]:fn:sys:[struct<mutable role>.to_immutable]]
            [def:const:bad_expr:[0::X]:at_expr]
        """)

        chkConstErr("f().name", "ct_err:[def:const:bad_expr:[0::X]:entity_attr][def:const:bad_expr:[0::X]:fn:f]")
        chkConstErr("f().to_struct()", "ct_err:[def:const:bad_expr:[0::X]:entity_to_struct][def:const:bad_expr:[0::X]:fn:f]")
    }

    @Test fun testExprObject() {
        def("object state { v: integer = 123; }")

        chkCompile("val X = state;", "ct_err:[def:const:bad_type:not_pure:[0::X]:[state]][def:const:bad_expr:[0::X]:object]")

        //TODO shall be one error
        chkCompile("val X = state.v;", "ct_err:[def:const:bad_expr:[0::X]:object][def:const:bad_expr:[0::X]:object_attr]")

        //TODO shall be one error
        chkCompile("val X = state.to_struct();",
            "ct_err:[def:const:bad_expr:[0::X]:object][def:const:bad_expr:[0::X]:object_to_struct]")

        chkCompile("val X = state.to_mutable_struct();", """ct_err:
            [def:const:bad_type:mutable:[0::X]:[struct<mutable state>]]
            [def:const:bad_expr:[0::X]:object]
            [def:const:bad_expr:[0::X]:object_to_struct]
        """)
    }

    @Test fun testExprAt() {
        def("entity user { name; }")

        chkConstErr("user @ {}", "ct_err:[def:const:bad_type:not_pure:[0::X]:[user]][def:const:bad_expr:[0::X]:at_expr]")
        chkConstErr("user @ {} (.name)", "ct_err:[def:const:bad_expr:[0::X]:at_expr][def:const:bad_expr:[0::X]:entity_attr]")

        chkConstErr("user @ { exists ( (u2:user) @* {} ) } ( .name )",
            "ct_err:[def:const:bad_expr:[0::X]:at_expr][def:const:bad_expr:[0::X]:at_expr][def:const:bad_expr:[0::X]:entity_attr]")

        chkConst("[1,2,3,4,5] @ {} ( @sum $ )", "int[15]")
    }

    @Test fun testExprOpContext() {
        val err = "def:const:bad_expr:[0::X]:fn"

        chkConstErr("op_context.last_block_time", "ct_err:[$err:prop:[op_context.last_block_time]][op_ctx:noop]")
        chkConstErr("op_context.block_height", "ct_err:[$err:prop:[op_context.block_height]][op_ctx:noop]")
        chkConstErr("op_context.op_index", "ct_err:[$err:prop:[op_context.op_index]][op_ctx:noop]")

        chkConstErr("op_context.transaction",
            "ct_err:[def:const:bad_type:not_pure:[0::X]:[transaction]][$err:prop:[op_context.transaction]][op_ctx:noop]")

        chkConstErr("op_context.is_signer(x'1234')", "ct_err:[$err:sys:[op_context.is_signer]][op_ctx:noop]")
        chkConstErr("is_signer(x'1234')", "ct_err:[$err:sys:[is_signer]][op_ctx:noop]")

        chkConstErr("op_context.get_signers()",
            "ct_err:[def:const:bad_type:mutable:[0::X]:[list<byte_array>]][$err:sys:[op_context.get_signers]][op_ctx:noop]")

        chkConstErr("op_context.get_all_operations()",
            "ct_err:[def:const:bad_type:mutable:[0::X]:[list<gtx_operation>]][$err:sys:[op_context.get_all_operations]][op_ctx:noop]")

        chkConstErr("op_context.emit_event('', gtv.from_json('{}'))",
            "ct_err:[type:def:const:unit:X][$err:sys:[op_context.emit_event]][op_ctx:noop]")
    }

    @Test fun testExprStruct() {
        def("struct s { x: integer = 123; }")
        def("struct p { mutable x: integer = 123; }")

        chkConstErr("s()", "ct_err:def:const:bad_expr:[0::X]:attr_default_value:x")
        chkConst("s(456)", "s[x=int[456]]")
        chkConstErr("p()", "ct_err:[def:const:bad_type:mutable:[0::X]:[p]][def:const:bad_expr:[0::X]:attr_default_value:x]")
        chkConstErr("p(456)", "ct_err:def:const:bad_type:mutable:[0::X]:[p]")
        chkConstErr("p(x = 456)", "ct_err:def:const:bad_type:mutable:[0::X]:[p]")

        chkConst("s(123).to_bytes()", "byte_array[a5073005a30302017b]")
        chkConst("s(123).to_gtv()", "gtv[[123]]")
    }

    @Test fun testExprSysFnSpecial() {
        chkCompile("val X = print();", "ct_err:[def:const:bad_expr:[0::X]:fn:sys:[print]][type:def:const:unit:X]")
        chkCompile("val X = log();", "ct_err:[def:const:bad_expr:[0::X]:fn:sys:[log]][type:def:const:unit:X]")

        chkCompile("val X = _type_of(0);", "OK")
        chkCompile("val X = _nullable(0);", "OK")

        chkCompile("val X = require(true);", "ct_err:type:def:const:unit:X")
        chkCompile("val X = require(_nullable(0));", "OK")
        chkCompile("val X = require_not_empty(_nullable(0));", "OK")
        chkCompile("val X = require_not_empty([1]).size();", "OK")
    }

    @Test fun testExprSysFnGtv() {
        chkConst("integer.from_gtv(gtv.from_json('123'))", "int[123]")
        chkConst("text.from_gtv(gtv.from_json('\"Hello\"'))", "text[Hello]")
        chkConst("boolean.from_gtv(gtv.from_json('0'))", "boolean[false]")

        chkConst("(123).to_gtv()", "gtv[123]")
        chkConst("'Hello'.to_gtv()", """gtv["Hello"]""")
        chkConst("true.to_gtv()", "gtv[1]")

        chkConst("(123).hash()", "byte_array[1100c41df25b87fee6921937b38c863d05445bc20d8760ad282c8c7d220e844b]")
        chkConst("'Hello'.hash()", "byte_array[fe1937fa779c7ef4cbfdab18264a12151a93b584490a0bc23e171e71790af174]")

        chkConst("list<integer>.from_gtv(gtv.from_json('[1,2,3]')).size()", "int[3]")
        chkConst("set<integer>.from_gtv(gtv.from_json('[1,2,3]')).size()", "int[3]")
        chkConst("map<integer,text>.from_gtv(gtv.from_json('[]')).size()", "int[0]")

        chkConst("[1,2,3].to_gtv()", "gtv[[1, 2, 3]]")
        chkConst("set([1,2,3]).to_gtv()", "gtv[[1, 2, 3]]")
        chkConst("[1:'A',2:'B'].to_gtv()", """gtv[[[1, "A"], [2, "B"]]]""")
        chkConst("[1,2,3].hash()", "byte_array[8a6ec7112c4e652c1d6971525a2fbebd9a26d38c026a7eb5bde8aaa54fd57101]")
    }

    @Test fun testExprSysFnBasicTypes() {
        chkConst("abs(-123)", "int[123]")
        chkConst("min(123, 456)", "int[123]")
        chkConst("max(123, 456)", "int[456]")
        chkConst("json('{}')", "json[{}]")
        chkConst("empty([123])", "boolean[false]")
        chkConst("exists([123])", "boolean[true]")

        chkConst("sha256(x'')", "byte_array[e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855]")
        chkConst("keccak256(x'')", "byte_array[c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470]")
        chkConstErr("unit()", "ct_err:type:def:const:unit:X")

        chkConst("integer('123')", "int[123]")
        chkConst("integer.from_hex('1234')", "int[4660]")
        chkConst("integer.MIN_VALUE", "int[-9223372036854775808]")
        chkConst("integer.MAX_VALUE", "int[9223372036854775807]")
        chkConst("(-123).abs()", "int[123]")
        chkConst("(123).min(456)", "int[123]")
        chkConst("(123).max(456)", "int[456]")

        chkConst("decimal('123')", "dec[123]")
        chkConst("decimal.from_text('12.34')", "dec[12.34]")
        chkConst("decimal.PRECISION", "int[131092]")
        chkConst("decimal.SCALE", "int[20]")
        chkConst("decimal.INT_DIGITS", "int[131072]")
        chkConst("(-12.34).abs()", "dec[12.34]")
        chkConst("(12.34).floor()", "dec[12]")
        chkConst("(12.34).ceil()", "dec[13]")
        chkConst("(12.34).round()", "dec[12]")

        chkConst("text.from_bytes(x'313233')", "text[123]")
        chkConst("'hello'.size()", "int[5]")
        chkConst("'hello'.upper_case()", "text[HELLO]")
        chkConst("'hello'.sub(2, 4)", "text[ll]")

        chkConst("byte_array('1234')", "byte_array[1234]")
        chkConst("x'1234'.size()", "int[2]")
        chkConst("x'1234'.to_hex()", "text[1234]")

        chkConst("range(100)", "range[0,100,1]")
    }

    @Test fun testExprSysFnCollections() {
        chkConst("[1,2,3,4,5].empty()", "boolean[false]")
        chkConst("[1,2,3,4,5].size()", "int[5]")
        chkConst("[1,2,3,4,5][2]", "int[3]")

        chkConst("set([1,2,3,4,5]).empty()", "boolean[false]")
        chkConst("set([1,2,3,4,5]).size()", "int[5]")

        chkConst("[1:'A',2:'B',3:'C'].empty()", "boolean[false]")
        chkConst("[1:'A',2:'B',3:'C'].size()", "int[3]")
        chkConst("[1:'A',2:'B',3:'C'][2]", "text[B]")
    }

    @Test fun testExprTestApi() {
        tst.testLib = true

        chkConst("rell.test.keypairs.bob",
                "rell.test.keypair[pub=byte_array[034f355bdcb7cc0af728ef3cceb9615d90684bb5b2ca5f859ab0f0b704075871aa]," +
                        "priv=byte_array[1111111111111111111111111111111111111111111111111111111111111111]]")

        chkConst("rell.test.pubkeys.bob", "byte_array[034f355bdcb7cc0af728ef3cceb9615d90684bb5b2ca5f859ab0f0b704075871aa]")
        chkConst("rell.test.privkeys.bob", "byte_array[1111111111111111111111111111111111111111111111111111111111111111]")

        chkConstErr("assert_null(_nullable_int(null))", "ct_err:type:def:const:unit:X")
        chkConstErr("assert_not_null(_nullable(123))", "ct_err:type:def:const:unit:X")
        chkConstErr("assert_equals(123, 456)", "ct_err:type:def:const:unit:X")
        chkConstErr("assert_not_equals(123, 456)", "ct_err:type:def:const:unit:X")
        chkConstErr("assert_lt(123, 456)", "ct_err:type:def:const:unit:X")
        chkConstErr("assert_gt_lt(123, 456, 789)", "ct_err:type:def:const:unit:X")

        val errBadType = "def:const:bad_type:not_pure:[0::X]:[rell.test.failure]"
        val errAssertFails = "def:const:bad_expr:[0::X]:fn:sys:[rell.test.assert_fails]"
        chkConstErr("assert_fails(integer.from_hex('1234', *))",
            "ct_err:[$errBadType][$errAssertFails][def:const:bad_expr:[0::X]:partial_call]")
        chkConstErr("assert_fails('hello', integer.from_hex('1234', *))",
            "ct_err:[$errBadType][$errAssertFails][def:const:bad_expr:[0::X]:partial_call]")
    }

    @Test fun testExprEnum() {
        def("enum color { red, green, blue }")
        chkConst("color.values().size()", "int[3]")
        chkConst("color.red.name", "text[red]")
        chkConst("color.red.value", "int[0]")
    }

    @Test fun testExprCollectionAt() {
        def("val X: integer = [1,2,3,4,5] @ {} ( @sum $ );")
        def("val Y: integer = (a:[1,2,3,4,5]) @ {} ( @sum (b:[1,2,3,4,5]) @ {} ( @sum a * b ) );")
        chk("X", "int[15]")
        chk("Y", "int[225]")
    }

    @Test fun testType() {
        chkCompile("val X: unit = 0;", "ct_err:[type:def:const:unit:X][def:const_expr_type:[unit]:[integer]]")
        chkCompile("val X: unit = unit();", "ct_err:type:def:const:unit:X")
        chkCompile("val X = unit();", "ct_err:type:def:const:unit:X")
        chkCompile("val X = null;", "OK")

        chkCompile("val X: integer = 'hello';", "ct_err:def:const_expr_type:[integer]:[text]")
        chkCompile("val X: text = 123;", "ct_err:def:const_expr_type:[text]:[integer]")
        chkCompile("val X: integer = _nullable_int(123);", "ct_err:def:const_expr_type:[integer]:[integer?]")
    }

    @Test fun testTypeSimple() {
        def("entity user { name; }")
        def("enum color { red, green, blue }")

        chkType("true", "boolean", "boolean[true]")
        chkType("'hello'", "text", "text[hello]")
        chkType("123", "integer", "int[123]")
        chkType("12.34", "decimal", "dec[12.34]")
        chkType("x'1234'", "byte_array", "byte_array[1234]")
        chkType("rowid(123)", "rowid", "rowid[123]")
        chkType("json('{}')", "json", "json[{}]")
        chkType("color.red", "color", "color[red]")
        chkType("range(100)", "range", "range[0,100,1]")
        chkType("gtv.from_json('{}')", "gtv", "gtv[[:]]")

        chkType("_nullable(123)", "integer?", "int[123]")
        chkType("(123,'hello')", "(integer,text)", "(int[123],text[hello])")
        chkType("(a=123,b='hello')", "(a:integer,b:text)", "(a=int[123],b=text[hello])")

        chkTypeErr("user", "not_pure", "user")

        chkCompile("val X: unit = 0;", "ct_err:[type:def:const:unit:X][def:const_expr_type:[unit]:[integer]]")
    }

    @Test fun testTypeCollection() {
        chkCompile("val X = [1,2,3];", "ct_err:def:const:bad_type:mutable:[0::X]:[list<integer>]")
        chkCompile("val X = set([1,2,3]);", "ct_err:def:const:bad_type:mutable:[0::X]:[set<integer>]")
        chkCompile("val X = [1:'A',2:'B'];", "ct_err:def:const:bad_type:mutable:[0::X]:[map<integer,text>]")

        chkCompile("val X = list<integer>();", "ct_err:def:const:bad_type:mutable:[0::X]:[list<integer>]")
        chkCompile("val X = set<integer>();", "ct_err:def:const:bad_type:mutable:[0::X]:[set<integer>]")
        chkCompile("val X = map<integer,text>();", "ct_err:def:const:bad_type:mutable:[0::X]:[map<integer,text>]")
    }

    @Test fun testTypeOther() {
        def("struct s { x: integer; }")

        chkTypeErr("() -> text", "not_pure", "()->text")
        chkTypeErr("(integer) -> text", "not_pure", "(integer)->text")

        chkTypeErr("virtual<list<integer>>", "not_pure", "virtual<list<integer>>")
        chkTypeErr("virtual<set<integer>>", "not_pure", "virtual<set<integer>>")
        chkTypeErr("virtual<map<text,integer>>", "not_pure", "virtual<map<text,integer>>")
        chkTypeErr("virtual<(integer,text)>", "not_pure", "virtual<(integer,text)>")
        chkTypeErr("virtual<s>", "not_pure", "virtual<s>")
    }

    @Test fun testTypeTest() {
        tst.testLib = true

        chkType("rell.test.keypair(priv = x'12', pub = x'34')", "rell.test.keypair",
                "rell.test.keypair[pub=byte_array[34],priv=byte_array[12]]")

        chkTypeErr("rell.test.block", "mutable", "rell.test.block")
        chkTypeErr("rell.test.tx", "mutable", "rell.test.tx")
        chkTypeErr("rell.test.op", "not_pure", "rell.test.op")
    }

    @Test fun testTypeStruct() {
        chkTypeStruct("struct s { x: integer; }", "s(123)", "s[x=int[123]]")
        chkTypeStruct("struct s { mutable x: integer; }", "s(123)", "ct_err:def:const:bad_type:mutable:[0::X]:[s]")

        chkTypeStruct("struct s { x: list<integer>; }", "s([])", "ct_err:def:const:bad_type:mutable:[0::X]:[s]")
        chkTypeStruct("struct s { x: set<integer>; }", "s(set())", "ct_err:def:const:bad_type:mutable:[0::X]:[s]")
        chkTypeStruct("struct s { x: map<integer,text>; }", "s([:])", "ct_err:def:const:bad_type:mutable:[0::X]:[s]")

        chkTypeStruct("entity user { name; } struct s { u: user?; }", "s(null)",
            "ct_err:def:const:bad_type:not_pure:[0::X]:[s]")
        chkTypeStruct("entity user { name; } struct s { u: struct<user>?; }", "s(null)", "s[u=null]")
        chkTypeStruct("entity user { name; } struct s { u: struct<mutable user>?; }", "s(null)",
                "ct_err:def:const:bad_type:mutable:[0::X]:[s]")

        chkTypeStruct("struct p { mutable x: integer; } struct s { p: p?; }", "s(null)",
                "ct_err:def:const:bad_type:mutable:[0::X]:[s]")
        chkTypeStruct("struct p { x: list<integer>; } struct s { p: p?; }", "s(null)",
                "ct_err:def:const:bad_type:mutable:[0::X]:[s]")

        chkTypeStruct("struct s { x: integer = 123; }", "s(456)", "s[x=int[456]]")
        chkTypeStruct("struct s { x: integer = 123; }", "s()", "ct_err:def:const:bad_expr:[0::X]:attr_default_value:x")
    }

    private fun chkTypeStruct(def: String, expr: String, expected: String) {
        chkFull("$def val X: s = $expr; query q() = X;", expected)
    }

    @Test fun testTypePromotion() {
        def("val X: decimal = 123;")
        def("val Y: decimal? = 123;")
        def("val Z: decimal? = _nullable_int(123);")

        chk("_type_of(X)", "text[decimal]")
        chk("_type_of(Y)", "text[decimal?]")
        chk("_type_of(Z)", "text[decimal?]")

        chk("X", "dec[123]")
        chk("Y", "dec[123]")
        chk("Z", "dec[123]")
    }

    @Test fun testNullAnalysis() {
        def("val X: integer? = _nullable_int(123);")
        def("val Y: integer? = _nullable_int(null);")

        chk("_type_of(X)", "text[integer?]")
        chk("_type_of(Y)", "text[integer?]")

        chk("if (X != null) _type_of(X) else ''", "text[integer]")
        chk("if (Y != null) _type_of(X) else ''", "text[]")
        chk("if (X??) _type_of(X) else ''", "text[integer]")
        chk("if (Y??) _type_of(X) else ''", "text[]")

        chkEx("{ X!!; return _type_of(X); }", "text[integer]")

        chkWarn()
        chk("if (X != null) X!! else 0", "int[123]")
        chkWarn("expr:smartnull:const:never:[X]")
        chk("if (Y == null) Y!! else 1", "rt_err:null_value")
        chkWarn("expr:smartnull:const:always:[Y]")
    }

    @Test fun testAssignment() {
        def("val X: integer = 123;")
        chkEx("{ X = 456; return X; }", "ct_err:expr_bad_dst")
        chkEx("{ X += 456; return X; }", "ct_err:expr_bad_dst")
        chkEx("{ X++; return X; }", "ct_err:expr_bad_dst")
        chkEx("{ ++X; return X; }", "ct_err:expr_bad_dst")
    }

    @Test fun testImplicitAtWheerAttr() {
        tstCtx.useSql = true
        def("val foo = 123;")
        def("namespace ns { val bar = 456; }")
        def("entity data { foo: integer; bar: integer; }")
        insert("c0.data", "foo,bar", "10,123,456")
        chk("data @? { foo }", "data[10]")
        chk("data @? { ns.bar }", "data[10]")
    }

    private fun chkConst(expr: String, expected: String) {
        val t = copyTester()
        val def = "val X = $expr;"
        chkCompile(def, "OK")
        t.def(def)
        t.chk("X", expected)
    }

    private fun chkConstErr(expr: String, expected: String) {
        chkCompile("val X = $expr;", expected)
    }

    private fun chkType(expr: String, expType: String, expValue: String) {
        val t = copyTester()
        val def = "val X = $expr;"
        chkCompile(def, "OK")
        t.def(def)
        t.chk("_type_of(X)", "text[$expType]")
        t.chk("X", expValue)
    }

    private fun chkTypeErr(type: String, typeErr: String, typeCode: String) {
        chkCompile("val X: $type = 0;",
                "ct_err:[def:const:bad_type:$typeErr:[0::X]:[$typeCode]][def:const_expr_type:[$typeCode]:[integer]]")
    }

    private fun copyTester(): RellCodeTester {
        val t = RellCodeTester(tstCtx)
        t.testLib = tst.testLib
        t.def(tst.defs)
        return t
    }
}
