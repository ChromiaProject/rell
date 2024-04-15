/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.testutils.unwrap
import net.postchain.rell.gtx.testutils.BaseGtxTest
import org.junit.Test

class ChainContextTest: BaseGtxTest() {
    @Test fun testRawConfig() {
        val ver = RellTestUtils.RELL_VER

        chk("chain_context.raw_config", """{'gtx':{'rell':{
            'compilerVersion':'$ver',
            'moduleArgs':{},
            'modules':[''],
            'sources':{'main.rell':'query q() = chain_context.raw_config;'},
            'strictGtvConversion':0,
            'version':'$ver'
        }}}""".unwrap())

        tst.moduleArgs("" to "'bar'")
        chk("chain_context.raw_config", """{'gtx':{'rell':{
            'compilerVersion':'$ver',
            'moduleArgs':{'':'bar'},
            'modules':[''],
            'sources':{'main.rell':'query q() = chain_context.raw_config;'},
            'strictGtvConversion':0,
            'version':'$ver'
        }}}""".unwrap())
    }

    @Test fun testModuleArgs() {
        def("struct module_args { s: text; n: integer; }")
        chkUserMistake("", "Module initialization failed: No moduleArgs for module '' in blockchain configuration")

        tst.moduleArgs("" to "{'s':'Hello','n':123}")
        chk("_type_of(chain_context.args)", "'module_args'")
        chk("chain_context.args", "{'n':123,'s':'Hello'}")
        chk("chain_context.args.s", "'Hello'")
        chk("chain_context.args.n", "123")
        chk("chain_context.args.x", "ct_err:unknown_member:[module_args]:x")

        tst.moduleArgs("" to "{'s':'Hello','n':123}")
        chk("chain_context.args", "{'n':123,'s':'Hello'}")

        tst.moduleArgs("" to "{'s':'Hello','n':123}", "foo.bar" to "{'x':123}")
        chk("chain_context.args", "{'n':123,'s':'Hello'}")

        tst.moduleArgs("" to "['Hello',123]")
        chk("chain_context.args", "{'n':123,'s':'Hello'}")
    }

    @Test fun testModuleArgsNoStruct() {
        chkCompile("entity module_args {}", "OK")
        chk("chain_context.args", "ct_err:expr_chainctx_args_norec")
    }

    @Test fun testModuleArgsStruct() {
        def("struct module_args { x: integer; y: text; }")
        tst.moduleArgs("" to "[123,'Hello']")
        chk("chain_context.args", "{'x':123,'y':'Hello'}")
    }

    @Test fun testModuleArgsBad() {
        def("struct module_args { s: text; n: integer; }")

        val err = "Module initialization failed"
        tst.moduleArgs()
        chkUserMistake("", "$err: No moduleArgs for module '' in blockchain configuration")

        tst.moduleArgs("" to "12345")
        chkUserMistake("", "$err: Decoding type 'module_args': expected ARRAY, actual INTEGER")

        tst.moduleArgs("" to "{'p':'Hello','q':123}")
        chkUserMistake("", "$err: Key missing in Gtv dictionary: ")

        tst.moduleArgs("" to "{'n':'Hello','s':123}")
        chkUserMistake("", "$err: Decoding type 'text': expected STRING, actual INTEGER")

        tst.moduleArgs("" to "{'s':'Hello','n':123,'p':456}")
        chkUserMistake("", "$err: Wrong key in Gtv dictionary for type 'module_args': 'p'")

        tst.moduleArgs("" to "{'s':'Hello'}")
        chkUserMistake("", "$err: Key missing in Gtv dictionary: field 'module_args.n'")

        tst.moduleArgs("" to "{'n':123}")
        chkUserMistake("", "$err: Key missing in Gtv dictionary: field 'module_args.s'")

        tst.moduleArgs("" to "['Hello']")
        chkUserMistake("", "$err: Wrong Gtv array size for struct 'module_args': 1 instead of 2")
    }

    @Test fun testModuleArgsDefaultValue() {
        def("struct module_args { s: text; n: integer = 123; }")

        tst.moduleArgs("" to "{'s':'Hello'}")
        chk("chain_context.args", "{'n':123,'s':'Hello'}")

        val err = "Module initialization failed"
        tst.moduleArgs("" to "{'n':456}")
        chkUserMistake("", "$err: Key missing in Gtv dictionary: field 'module_args.s'")

        tst.moduleArgs("" to "{'s':'Hello','n':456}")
        chk("chain_context.args", "{'n':456,'s':'Hello'}")

        tst.moduleArgs("" to "{'p':'Hello','n':456}")
        chkUserMistake("", "$err: Key missing in Gtv dictionary: field 'module_args.s'")

        tst.moduleArgs("" to "{'s':'Hello','x':456}")
        chkUserMistake("", "$err: Wrong key in Gtv dictionary for type 'module_args': 'x'")

        tst.moduleArgs("" to "['Hello']")
        chkUserMistake("", "$err: Wrong Gtv array size for struct 'module_args': 1 instead of 2")
    }

    @Test fun testModuleArgsDefaultValueAllAttrs() {
        file("a.rell", "module; val args = chain_context.args; struct module_args { x: integer; y: integer = 123; }")
        file("b.rell", "module; val args = chain_context.args; struct module_args { p: integer = 456; q: integer = 789; }")
        def("import a; import b;")

        tst.moduleArgs("a" to "{'x':111,'y':222}", "b" to "{'p':333,'q':444}")
        chk("a.args", "{'x':111,'y':222}")
        chk("b.args", "{'p':333,'q':444}")

        val err = "Module initialization failed"
        tst.moduleArgs("b" to "{'p':333,'q':444}")
        chkUserMistake("", "$err: No moduleArgs for module 'a' in blockchain configuration, but type module_args defined in the code")

        tst.moduleArgs("b" to "{'p':333,'q':444}")
        chkUserMistake("", "$err: No moduleArgs for module 'a' in blockchain configuration, but type module_args defined in the code")

        tst.moduleArgs("a" to "{'x':111,'y':222}")
        chk("a.args", "{'x':111,'y':222}")
        chk("b.args", "{'p':456,'q':789}")

        tst.moduleArgs("a" to "{'x':111}")
        chk("a.args", "{'x':111,'y':123}")
        chk("b.args", "{'p':456,'q':789}")
    }

    @Test fun testModuleArgsDefaultValueNested() {
        def("struct data { z: integer = 789; }")
        def("struct ref { y: integer = 456; d: data = data(987); }")
        def("struct module_args { x: integer = 123; r: ref = ref(654); }")

        chkModArgs("{'x':111,r:{y:222,d:{z:333}}}", "{'r':{'d':{'z':333},'y':222},'x':111}")
        chkModArgs("{'x':111,r:{y:222,d:{}}}", "{'r':{'d':{'z':789},'y':222},'x':111}")
        chkModArgs("{'x':111,r:{y:222}}", "{'r':{'d':{'z':987},'y':222},'x':111}")
        chkModArgs("{'x':111,r:{}}", "{'r':{'d':{'z':987},'y':456},'x':111}")
        chkModArgs("{'x':111}", "{'r':{'d':{'z':987},'y':654},'x':111}")
        chkModArgs("{}", "{'r':{'d':{'z':987},'y':654},'x':123}")

        chkModArgs("{r:{y:222,d:{z:333}}}", "{'r':{'d':{'z':333},'y':222},'x':123}")
        chkModArgs("{r:{d:{z:333}}}", "{'r':{'d':{'z':333},'y':456},'x':123}")
        chkModArgs("{r:{d:{}}}", "{'r':{'d':{'z':789},'y':456},'x':123}")
        chkModArgs("{r:{}}", "{'r':{'d':{'z':987},'y':456},'x':123}")
    }

    @Test fun testModuleArgsDefaultValueDependencies() {
        file("a.rell", """
            module;
            import b;
            val p: integer = b.r;
            val q: integer = chain_context.args.y;
            struct module_args { y: integer = b.s; }
        """)
        file("b.rell", """
            module;
            import a;
            val r: integer = chain_context.args.z;
            struct module_args { z: integer = a.q; }
            val s: integer = 123;
        """)
        def("import a; struct module_args { x: integer = a.p; }")

        chkModArgs("{x:321}", "{'x':321}")
        chkModArgs("{}", "{'x':123}")
    }

    @Test fun testModuleArgsDefaultValueRecursion() {
        tst.wrapRtErrors = false
        file("a.rell", "module; struct module_args { x: integer = chain_context.args.x; }")
        file("b.rell", "module; struct module_args { x: integer = chain_context.args.y; y: integer = 123; }")
        file("c.rell", "module; import d; val p: integer = chain_context.args.x; struct module_args { x: integer = d.q; }")
        file("d.rell", "module; import c; val q: integer = chain_context.args.y; struct module_args { y: integer = c.p; }")
        def("query q() = 0;")

        chkFull("import a;", "", "rt_err:const:recursion:modargs:a")
        chkFull("import b;", "", "rt_err:const:recursion:modargs:b")
        chkFull("import c;", "", "rt_err:const:recursion:const:0:c:p")
        chkFull("import d;", "", "rt_err:const:recursion:const:0:d:q")
    }

    @Test fun testModuleArgsDefaultValueVersionControl() {
        def("struct module_args { s: text; n: integer = 123; }")

        tst.moduleArgs("" to "{'s':'Hello'}")
        tst.compatibilityVer(null)
        chk("chain_context.args", "{'n':123,'s':'Hello'}")

        tst.compatibilityVer("0.13.5")
        chk("chain_context.args", "{'n':123,'s':'Hello'}")

        tst.compatibilityVer("0.13.4")
        chkUserMistake("", "Module initialization failed: Key missing in Gtv dictionary: field 'module_args.n'")
    }

    @Test fun testModuleArgsDefaultValueVersionControlAllAttrs() {
        def("struct module_args { s: text = 'Hello'; n: integer = 123; }")

        tst.moduleArgs()
        tst.compatibilityVer(null)
        chk("chain_context.args", "{'n':123,'s':'Hello'}")

        tst.compatibilityVer("0.13.5")
        chk("chain_context.args", "{'n':123,'s':'Hello'}")

        tst.compatibilityVer("0.13.4")
        chkUserMistake("", "Module initialization failed: No moduleArgs for module '' in blockchain configuration")
    }

    private fun chkModArgs(args: String, expected: String) {
        tst.moduleArgs("" to args)
        chk("chain_context.args", expected)
    }

    @Test fun testModuleArgsMultiModule() {
        file("lib/a.rell", "module; struct module_args { x: text; } function f(): module_args = chain_context.args;")
        file("lib/b.rell", "module; struct module_args { y: integer; } function f(): module_args = chain_context.args;")
        file("lib/c.rell", "module; struct module_args { z: decimal; } function f(): module_args = chain_context.args;")
        def("import lib.a; import lib.b; import lib.c;")

        tst.moduleArgs("lib.a" to "{x:'Hello'}", "lib.b" to "{'y':123}", "lib.c" to "{'z':'456.789'}")
        chk("a.f()", "{'x':'Hello'}")
        chk("b.f()", "{'y':123}")
        chk("c.f()", "{'z':'456.789'}")
        chk("a.chain_context", "ct_err:unknown_name:[a]:chain_context")
        chk("a.chain_context.args", "ct_err:unknown_name:[a]:chain_context")

        tst.moduleArgs("lib.a" to "{x:'Hello'}", "lib.b" to "{'y':123}", "lib.c" to "{'q':'456.789'}")
        chkUserMistake("", "Module initialization failed: Key missing in Gtv dictionary")

        tst.moduleArgs()
        chkUserMistake("", "Module initialization failed: No moduleArgs for module 'lib.a' in blockchain configuration")

        tst.moduleArgs("lib.a" to "{x:'Hello'}", "lib.b" to "{'y':123}", "lib.d" to "{'z':'456.789'}")
        chkUserMistake("", "Module initialization failed: No moduleArgs for module 'lib.c' in blockchain configuration")
    }

    @Test fun testModuleArgsAttrTypes() {
        chkCompile("struct module_args { x: integer; }", "OK")
        chkCompile("struct module_args { mutable x: integer; }", "ct_err:module_args:attr:mutable:module_args:x")

        chkCompile("struct module_args { x: list<integer>; }", "ct_err:module_args:attr:mutable_type:module_args:x")
        chkCompile("struct module_args { x: set<integer>; }", "ct_err:module_args:attr:mutable_type:module_args:x")
        chkCompile("struct module_args { x: map<text, integer>; }", "ct_err:module_args:attr:mutable_type:module_args:x")

        chkCompile("struct module_args { x: (a: integer, text); }", "OK")
        chkCompile("struct module_args { x: range; }", "ct_err:module_args:attr:no_gtv:module_args:x")
        chkCompile("struct module_args { x: virtual<list<integer>>; }", "ct_err:module_args:attr:not_pure:module_args:x")

        chkCompile("entity user { name; } struct module_args { u: user; }", "ct_err:module_args:attr:not_pure:module_args:u")
        chkCompile("entity user { name; } struct module_args { u: user?; }", "ct_err:module_args:attr:not_pure:module_args:u")
        chkCompile("entity user { name; } struct module_args { u: (user, text)?; }",
                "ct_err:module_args:attr:not_pure:module_args:u")

        chkCompile("entity user { name; } struct module_args { u: struct<user>; }", "OK")
        chkCompile("entity user { name; } struct module_args { u: struct<mutable user>; }",
                "ct_err:module_args:attr:mutable_type:module_args:u")

        chkCompile("entity user { name; } entity ref { u: user; } struct module_args { r: struct<ref>; }",
                "ct_err:module_args:attr:not_pure:module_args:r")
        chkCompile("entity user { name; } entity ref { u: user; } struct module_args { r: struct<mutable ref>; }",
                "ct_err:module_args:attr:mutable_type:module_args:r")

        chkCompile("entity user { name; } struct s { u: user?; } struct module_args { s: s; }",
                "ct_err:module_args:attr:not_pure:module_args:s")

        chkCompile("struct module_args { p: (integer) -> text; }", "ct_err:module_args:attr:no_gtv:module_args:p")
    }

    @Test fun testBlockchainRid() {
        val exp = RellTestUtils.strToRidHex("DEADBEEF")
        chk("chain_context.blockchain_rid", "'$exp'")
    }
}
