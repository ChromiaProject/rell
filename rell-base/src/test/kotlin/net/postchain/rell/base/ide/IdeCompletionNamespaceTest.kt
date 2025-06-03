/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.dsl.BaseLTest
import net.postchain.rell.base.testutils.unwrap
import org.junit.Test
import kotlin.test.assertEquals

class IdeCompletionNamespaceTest: BaseIdeCompletionTest() {
    @Test fun testBasic() {
        chkComps("val X = 123; entity data {} struct rec {} function f() {}",
            "X|CONSTANT|:X||integer|",
            "data|ENTITY|:data||-|",
            "f|FUNCTION|:f|()|unit|",
            "rec|STRUCT|:rec||-|", "rec|CONSTRUCTOR|:rec|()|-|",
        )
    }

    @Test fun testDefs() {
        defaultLib = true

        chkComps("struct rec {}", "rec|STRUCT|:rec||-|", "rec|CONSTRUCTOR|:rec|()|-|")
        chkComps("entity data {}", "data|ENTITY|:data||-|")
        chkComps("object state {}", "state|OBJECT|:state||-|")
        chkComps("enum color {}", "color|ENUM|:color||-|")
        chkComps("function f(x: text, y: decimal) = 123;", "f|FUNCTION|:f|(x: text, y: decimal)|integer|")
        chkComps("operation op(x: text) {}", "op|OPERATION|:op|(x: text)|-|")
        chkComps("query q(x: text) = 0;", "q|QUERY|:q|(x: text)|integer|")
        chkComps("val X = 123;", "X|CONSTANT|:X||integer|")
        chkComps("namespace ns {}", "ns|NAMESPACE|:ns||-|")
    }

    @Test fun testDefsLocation() {
        defaultLib = true

        val ns = "foo.bar:a.b"
        chkDefLoc("struct rec {}", "rec|STRUCT|foo.bar:a.b.rec||-|$ns", "rec|CONSTRUCTOR|foo.bar:a.b.rec|()|-|$ns")
        chkDefLoc("entity data {}", "data|ENTITY|foo.bar:a.b.data||-|$ns")
        chkDefLoc("object state {}", "state|OBJECT|foo.bar:a.b.state||-|$ns")
        chkDefLoc("enum color {}", "color|ENUM|foo.bar:a.b.color||-|$ns")
        chkDefLoc("function f(x: text) = 123;", "f|FUNCTION|foo.bar:a.b.f|(x: text)|integer|$ns")
        chkDefLoc("operation op(x: text) {}", "op|OPERATION|foo.bar:a.b.op|(x: text)|-|$ns")
        chkDefLoc("query q(x: text) = 0;", "q|QUERY|foo.bar:a.b.q|(x: text)|integer|$ns")
        chkDefLoc("val X = 123;", "X|CONSTANT|foo.bar:a.b.X||integer|$ns")
        chkDefLoc("namespace ns {}", "ns|NAMESPACE|foo.bar:a.b.ns||-|$ns")
    }

    private fun chkDefLoc(code: String, vararg exp: String) {
        resetTst()
        file("foo/bar.rell", "module; namespace a.b { $code }")
        chkComps("import foo.bar.{a.b.*};", *exp)
    }

    @Test fun testImport() {
        file("a/b/lib.rell", "module; entity data {}")
        chkComps("import a.b.lib;", "lib|IMPORT|:lib||a.b.lib|")
        chkComps("import bil: a.b.lib;", "bil|IMPORT|:bil||a.b.lib|")
        chkComps("import a.b.lib.*;", "data|ENTITY|a.b.lib:data||-|a.b.lib")
        chkComps("import ns: a.b.lib.*;", "ns|IMPORT|:ns||a.b.lib.*|")
    }

    @Test fun testImportExact() {
        file("a/b/lib.rell", "module; namespace ns { entity data {} }")
        chkComps("import a.b.lib.{ns.data};", "data|ENTITY|a.b.lib:ns.data||-|a.b.lib:ns")
        chkComps("import a.b.lib.{tada:ns.data};", "tada|ENTITY|:tada||-|")
        chkComps("import x: a.b.lib.{ns.data};", "x|IMPORT|:x||a.b.lib.{...}|")
        chkComps("import a.b.lib.{x:ns.*};", "x|IMPORT|:x||a.b.lib:ns.*|")
    }

    @Test fun testImportExactDetails() {
        defaultLib = true
        file("a.rell", "module; entity data {}")
        file("b.rell", "module; val X = 123;")
        file("c.rell", "module; function f(x: text) = 123;")

        chkComps("import a.*;", "data|ENTITY|a:data||-|a")
        chkComps("import a.{data};", "data|ENTITY|a:data||-|a")
        chkComps("import a.{tada:data};", "tada|ENTITY|:tada||-|")

        chkComps("import b.*;", "X|CONSTANT|b:X||integer|b")
        chkComps("import b.{X};", "X|CONSTANT|b:X||integer|b")
        chkComps("import b.{Y:X};", "Y|CONSTANT|:Y||integer|")

        chkComps("import c.*;", "f|FUNCTION|c:f|(x: text)|integer|c")
        chkComps("import c.{f};", "f|FUNCTION|c:f|(x: text)|integer|c")
        chkComps("import c.{g:f};", "g|FUNCTION|:g|(x: text)|integer|")
    }

    @Test fun testImportShadowing() {
        file("lib.rell", "module; enum data {}")
        file("bil.rell", "module; entity data {}")

        chkComps("function data() {}", "data|FUNCTION|:data|()|unit|")
        chkComps("import lib.*;", "data|ENUM|lib:data||-|lib")
        chkComps("import lib.*; function data() {}", "data|FUNCTION|:data|()|unit|")

        chkComps("import lib.*; import bil.*;", "data|ENUM|lib:data||-|lib", "data|ENTITY|bil:data||-|bil")
        chkComps("import lib.*; import bil.*; function data() {}", "data|FUNCTION|:data|()|unit|")
    }

    @Test fun testImportDuplication() {
        fullCompStr = false
        file("lib.rell", "module; entity data {}")
        file("a1.rell", "module; import lib.*;")
        file("a2.rell", "module; import lib.*;")
        file("b1.rell", "module; import lib.{data};")
        file("b2.rell", "module; import lib.{data};")

        chkComps("import a1.*; import a2.*;", "data|ENTITY|lib:data")
        //chkComps("import b1.*; import b2.*;", "data|ENTITY|lib:data") //TODO make this work
        //chkComps("import a1.*; import b1.*;", "data|ENTITY|lib:data") //TODO make this work
        chkComps("import a1.*; import lib.*;", "data|ENTITY|lib:data")
        //chkComps("import b1.*; import lib.*;", "data|ENTITY|lib:data") //TODO make this work
        chkComps("import a1.*; import lib.{data};", "data|ENTITY|lib:data")
        chkComps("import b1.*; import lib.{data};", "data|ENTITY|lib:data")
    }

    @Test fun testPosNamespace() {
        val (a, b) = arrayOf("A|CONSTANT|:A||integer|", "B|CONSTANT|:ns.B||integer|:ns")
        val code = "^0 val A = 123; ^1 namespace ns ^2 { ^3 val B = 456; ^4 } ^5"
        chkComps(code, -1, a, "ns|NAMESPACE|:ns||-|")
        chkComps(code, 0, a, "ns|NAMESPACE|:ns||-|")
        chkComps(code, 1, a, "ns|NAMESPACE|:ns||-|")
        chkComps(code, 2, a, "ns|NAMESPACE|:ns||-|")
        chkComps(code, 3, a, b, "ns|NAMESPACE|:ns||-|")
        chkComps(code, 4, a, b, "ns|NAMESPACE|:ns||-|")
        chkComps(code, 5, a, "ns|NAMESPACE|:ns||-|")
    }

    @Test fun testPosNamespaceNested() {
        fullCompStr = false
        val (aDef, bDef, cDef, dDef) = arrayOf("val A = 123", "val B = 456", "val C = 789", "val D = 987")
        val (a, b, c, d) = arrayOf("A|CONSTANT|:A", "B|CONSTANT|:x.B", "C|CONSTANT|:x.y.C", "D|CONSTANT|:x.y.z.D")
        val code = "^0 $aDef; ^1 namespace x ^2 { ^3 $bDef; ^4 namespace y ^5 { ^6 $cDef; namespace z { ^7 $dDef; } } }"

        chkComps(code, -1, a, "x|NAMESPACE|:x")
        chkComps(code, 0, a, "x|NAMESPACE|:x")
        chkComps(code, 1, a, "x|NAMESPACE|:x")
        chkComps(code, 2, a, "x|NAMESPACE|:x")
        chkComps(code, 3, a, b, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y")
        chkComps(code, 4, a, b, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y")
        chkComps(code, 5, a, b, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y")
        chkComps(code, 6, a, b, c, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y", "z|NAMESPACE|:x.y.z")
        chkComps(code, 7, a, b, c, d, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y", "z|NAMESPACE|:x.y.z")
    }

    @Test fun testPosOtherDefs() {
        val a = "A|CONSTANT|:A||integer|"
        val user = "user|ENTITY|:user||-|"

        var code = "val A = 123; entity user {} ^0 entity data { ^1 u: user; ^2 }"
        chkComps(code, -1, a, "data|ENTITY|:data||-|", user)
        chkComps(code, 0, a, "data|ENTITY|:data||-|", user)
        chkComps(code, 1, a, "data|ENTITY|:data||-|", user)
        chkComps(code, 2, a, "data|ENTITY|:data||-|", user)

        code = "val A = 123; entity user {} ^0 entity data { ^1 u: user; ^2 }"
        chkComps(code, -1, a, "data|ENTITY|:data||-|", user)
        chkComps(code, 0, a, "data|ENTITY|:data||-|", user)
        chkComps(code, 1, a, "data|ENTITY|:data||-|", user)
        chkComps(code, 2, a, "data|ENTITY|:data||-|", user)

        code = "val A = 123; ^0 enum color { ^1 red, ^2 green }"
        chkComps(code, -1, a, "color|ENUM|:color||-|")
        chkComps(code, 0, a, "color|ENUM|:color||-|")
        chkComps(code, 1, a, "color|ENUM|:color||-|")
        chkComps(code, 2, a, "color|ENUM|:color||-|")

        code = "val A = 123; entity user {} ^0 function f(^1 u: user ^2) {}"
        chkComps(code, -1, a, "f|FUNCTION|:f|(u: user)|unit|", user)
        chkComps(code, 0, a, "f|FUNCTION|:f|(u: user)|unit|", user)
        chkComps(code, 1, a, "f|FUNCTION|:f|(u: user)|unit|", user)
        chkComps(code, 2, a, "f|FUNCTION|:f|(u: user)|unit|", user)
    }

    @Test fun testMultiFile() {
        fullCompStr = false
        file("module.rell", "val X = 123;")
        file("foo.rell", "entity data {}")
        chkComps("function f() {}", "X|CONSTANT|:X", "data|ENTITY|:data", "f|FUNCTION|:f")
    }

    @Test fun testNamespaceComplex() {
        fullCompStr = false
        val code = "val A = 123; ^0 namespace x.y.z ^1 { ^2 val B = 456; ^3 } ^4"
        val (a, b) = arrayOf("A|CONSTANT|:A", "B|CONSTANT|:x.y.z.B")
        chkComps(code, -1, a, "x|NAMESPACE|:x")
        chkComps(code, 0, a, "x|NAMESPACE|:x")
        chkComps(code, 1, a, "x|NAMESPACE|:x")
    }

    @Test fun testNamespaceDisjoint() {
        fullCompStr = false
        val code = "val A = 123; ^0 namespace ns ^1 { ^2 val B = 456; ^3 } ^4 namespace ns { ^5 val C = 789; ^6 }"
        val (a, b, c) = arrayOf("A|CONSTANT|:A", "B|CONSTANT|:ns.B", "C|CONSTANT|:ns.C")
        val ns = "ns|NAMESPACE|:ns"
        chkComps(code, -1, a, ns)
        chkComps(code, 0, a, ns)
        chkComps(code, 1, a, ns)
        chkComps(code, 2, a, b, c, ns)
        chkComps(code, 3, a, b, c, ns)
        chkComps(code, 4, a, ns)
        chkComps(code, 5, a, b, c, ns)
        chkComps(code, 6, a, b, c, ns)
    }

    @Test fun testNamespaceDisjointComplex() {
        fullCompStr = false
        val code = """
            val A = 1;
            ^0 namespace x {
                ^1 val B = 2;
                ^2 namespace y { ^3 val C = 3; } ^4
            }
            ^5 namespace x.y { ^6 val D = 4; }
        """.unwrap()

        val (a, b, c, d) = arrayOf("A|CONSTANT|:A", "B|CONSTANT|:x.B", "C|CONSTANT|:x.y.C", "D|CONSTANT|:x.y.D")
        chkComps(code, -1, a, "x|NAMESPACE|:x")
        chkComps(code, 0, a, "x|NAMESPACE|:x")
        chkComps(code, 1, a, b, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y")
        chkComps(code, 2, a, b, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y")
        chkComps(code, 3, a, b, c, d, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y")
        chkComps(code, 4, a, b, "x|NAMESPACE|:x", "y|NAMESPACE|:x.y")
        chkComps(code, 5, a, "x|NAMESPACE|:x")
    }

    @Test fun testNamespaceDisjointMultiFile() {
        fullCompStr = false
        file("module.rell", "namespace ns { val B = 2; }")
        file("foo.rell", "namespace ns { val C = 3; }")

        val code = "val A = 1; ^0 namespace ns ^1 { ^2 val D = 4; ^3 } ^4"
        val ns = "ns|NAMESPACE|:ns"
        chkComps(code, -1, "A|CONSTANT|:A", ns)
        chkComps(code, 0, "A|CONSTANT|:A", ns)
        chkComps(code, 1, "A|CONSTANT|:A", ns)
        chkComps(code, 2, "A|CONSTANT|:A", "B|CONSTANT|:ns.B", "C|CONSTANT|:ns.C", "D|CONSTANT|:ns.D", ns)
        chkComps(code, 3, "A|CONSTANT|:A", "B|CONSTANT|:ns.B", "C|CONSTANT|:ns.C", "D|CONSTANT|:ns.D", ns)
        chkComps(code, 4, "A|CONSTANT|:A", ns)
    }

    @Test fun testNoDocSymbols() {
        val code = "entity data {}"
        var options = baseCompilerOptions().toBuilder().build()
        assertEquals(listOf("data|ENTITY|:data||-|"), calcComps(code, null, options))
        options = baseCompilerOptions().toBuilder().ideDocSymbolsEnabled(false).build()
        assertEquals(listOf("data|ENTITY|:data||-|"), calcComps(code, null, options))
    }

    @Test fun testLibDefs() {
        libModule {
            type("my_type")
            struct("my_struct") {}
            constant("C", 123L)
            property("prop", "integer") { value { _ -> Rt_UnitValue } }
            property("spec_prop", BaseLTest.makeNsProp())
            function("f", "my_type") {
                param("x", "my_struct")
                body { -> Rt_UnitValue}
            }
            function("g", BaseLTest.makeNsFun())
        }

        chkCompKeys("C", "f", "g", "my_struct", "my_type", "prop", "spec_prop")
        chkLibComps("C", "C|CONSTANT|test:C||integer|test")
        chkLibComps("f", "f|FUNCTION|test:f|(x: my_struct)|my_type|test")
        chkLibComps("g", "g|FUNCTION|test:g|()|-|test")
        chkLibComps("my_struct", "my_struct|STRUCT|test:my_struct||-|test",
            "my_struct|CONSTRUCTOR|test:my_struct|()|-|test")
        chkLibComps("my_type", "my_type|TYPE|test:my_type||-|test")
        chkLibComps("prop", "prop|PROPERTY|test:prop||integer|test")
        chkLibComps("spec_prop", "spec_prop|PROPERTY|test:spec_prop||-|test")
    }

    private fun chkLibComps(key: String, vararg expected: String) {
        val map = calcComps0("", null)
        val actual = map.get(key).toList()
        assertEquals(expected.toList(), actual)
    }

    @Test fun testLibDefsAliases() {
        libModule {
            struct("data") {}
            alias("tada", "data")
            function("f", "data") {
                alias("g")
                body { -> Rt_UnitValue }
            }
        }

        chkComps("",
            "data|STRUCT|test:data||-|test", "data|CONSTRUCTOR|test:data|()|-|test",
            "f|FUNCTION|test:f|()|data|test",
            "g|FUNCTION|test:g|()|data|test",
            "tada|STRUCT|test:tada||-|test", "tada|CONSTRUCTOR|test:tada|()|-|test",
        )
    }

    @Test fun testLibDefsAndUserDefs() {
        fullCompStr = false
        libModule {
            constant("C", 123L)
            property("my_prop", "integer") { value { _ -> Rt_UnitValue } }
        }

        chkComps("entity data {}", "C|CONSTANT|test:C", "data|ENTITY|:data", "my_prop|PROPERTY|test:my_prop")
        chkComps("val X = 456;", "C|CONSTANT|test:C", "X|CONSTANT|:X", "my_prop|PROPERTY|test:my_prop")
    }

    @Test fun testLibDefsVersionControl() {
        fullCompStr = false
        libModule {
            type("my_type", since = "0.11.0")
            constant("C", 123L, since = "0.13.0")
            property("my_prop", "integer", since = "0.12.0") { value { _ -> Rt_UnitValue } }
        }

        chkComps("", "C|CONSTANT|test:C", "my_prop|PROPERTY|test:my_prop", "my_type|TYPE|test:my_type")

        tst.compatibilityVer("0.10.10")
        chkComps("")
        tst.compatibilityVer("0.11.0")
        chkComps("", "my_type|TYPE|test:my_type")
        tst.compatibilityVer("0.12.0")
        chkComps("", "my_prop|PROPERTY|test:my_prop", "my_type|TYPE|test:my_type")
        tst.compatibilityVer("0.13.0")
        chkComps("", "C|CONSTANT|test:C", "my_prop|PROPERTY|test:my_prop", "my_type|TYPE|test:my_type")
        tst.compatibilityVer("0.13.5")
        chkComps("", "C|CONSTANT|test:C", "my_prop|PROPERTY|test:my_prop", "my_type|TYPE|test:my_type")
    }

    @Test fun testLibDefsDeprecated() {
        libModule {
            type("data")
            alias("w_data", "data", C_MessageType.WARNING)
            alias("e_data", "data", C_MessageType.ERROR)
        }

        chkComps("", "data|TYPE|test:data||-|test", "w_data|TYPE|test:w_data||-|test")

        tst.deprecatedError = true
        chkComps("", "data|TYPE|test:data||-|test")
    }

    @Test fun testLibOverloadedFunctions() {
        libModule {
            function("f", "integer") {
                body { -> Rt_UnitValue }
            }
            function("f", "integer") {
                param("x", "big_integer")
                body { -> Rt_UnitValue }
            }
            function("f", "integer") {
                param("x", "decimal")
                param("y", "text")
                body { -> Rt_UnitValue }
            }
        }

        chkComps("",
            "f|FUNCTION|test:f|()|integer|test",
            "f|FUNCTION|test:f|(x: big_integer)|integer|test",
            "f|FUNCTION|test:f|(x: decimal, y: text)|integer|test",
        )
    }

    @Test fun testLibOverloadedFunctionsAliases() {
        libModule {
            function("f", "integer") {
                alias("p")
                body { -> Rt_UnitValue }
            }
            function("g", "boolean") {
                alias("p")
                param("x", "big_integer")
                body { -> Rt_UnitValue }
            }
            function("h", "byte_array") {
                alias("p")
                param("x", "decimal")
                param("y", "text")
                body { -> Rt_UnitValue }
            }
        }

        chkComps("",
            "f|FUNCTION|test:f|()|integer|test",
            "g|FUNCTION|test:g|(x: big_integer)|boolean|test",
            "h|FUNCTION|test:h|(x: decimal, y: text)|byte_array|test",
            "p|FUNCTION|test:p|()|integer|test",
            "p|FUNCTION|test:p|(x: big_integer)|boolean|test",
            "p|FUNCTION|test:p|(x: decimal, y: text)|byte_array|test",
        )
    }

    @Test fun testLibTypeConstructor() {
        libModule {
            type("my_type") {
                constructor { body { -> Rt_UnitValue } }
                constructor {
                    param("x", "integer")
                    param("y", "text")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkComps("",
            "my_type|TYPE|test:my_type||-|test",
            "my_type|CONSTRUCTOR|test:my_type|()|-|test",
            "my_type|CONSTRUCTOR|test:my_type|(x: integer, y: text)|-|test",
        )
    }

    @Test fun testTypeConstructorAlias() {
        libModule {
            alias("my_alias", "my_type")
            type("my_type") {
                constructor { body { -> Rt_UnitValue } }
                constructor {
                    param("x", "integer")
                    param("y", "text")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkComps("",
            "my_alias|TYPE|test:my_alias||-|test",
            "my_alias|CONSTRUCTOR|test:my_alias|()|-|test",
            "my_alias|CONSTRUCTOR|test:my_alias|(x: integer, y: text)|-|test",
            "my_type|TYPE|test:my_type||-|test",
            "my_type|CONSTRUCTOR|test:my_type|()|-|test",
            "my_type|CONSTRUCTOR|test:my_type|(x: integer, y: text)|-|test",
        )
    }

    @Test fun testStructConstructor() {
        defaultLib = true
        val s = "data|STRUCT|:data||-|"
        chkComps("struct data {}", s, "data|CONSTRUCTOR|:data|()|-|")
        chkComps("struct data { x: integer; y: text; }", s, "data|CONSTRUCTOR|:data|(x: integer, y: text)|-|")
        chkComps("struct data { x: integer; p: boolean = true; y: text; q: json? = null; }", s,
            "data|CONSTRUCTOR|:data|(x: integer, y: text, p: boolean = ..., q: json? = ...)|-|")
    }
}
