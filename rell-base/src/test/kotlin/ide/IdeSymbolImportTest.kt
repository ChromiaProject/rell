/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import kotlin.test.Test

class IdeSymbolImportTest: BaseIdeSymbolTest() {
    @Test fun testImportExactBasic() {
        file("lib/f1.rell", "val k = 123;")
        file("lib/f2.rell", "function f() = 456;")

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|lib/f1.rell", "?doc=MODULE|lib|<module>")
        val kRef = arrayOf("k=DEF_CONSTANT|-|lib/f1.rell/constant[k]", "?head=CONSTANT|lib:k")
        val fRef = arrayOf("f=DEF_FUNCTION|-|lib/f2.rell/function[f]", "?head=FUNCTION|lib:f")

        chkSyms("import lib.{k}; query q() = k;", *lib, *kRef, *kRef)
        chkSyms("import lib.{f}; query q() = f();", *lib, *fRef, *fRef)
        chkSyms("import lib.{k,f}; query q() = k;", *lib, *kRef, *fRef, *kRef)
        chkSyms("import lib.{k,f}; query q() = f();", *lib, *kRef, *fRef, *fRef)
    }

    @Test fun testImportExactOuterAlias() {
        file("lib/f1.rell", "val k = 123;")
        file("lib/f2.rell", "function f() = 456;")

        val aDef = arrayOf("a=DEF_NAMESPACE|namespace[a]|-", "?doc=IMPORT|:a|<import> a: [lib].{...}")
        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|lib/f1.rell", "?head=MODULE|lib")
        val aRef = arrayOf("a=DEF_NAMESPACE|-|main.rell/namespace[a]", "?head=IMPORT|:a")
        val kRef = arrayOf("k=DEF_CONSTANT|-|lib/f1.rell/constant[k]", "?head=CONSTANT|lib:k")
        val fRef = arrayOf("f=DEF_FUNCTION|-|lib/f2.rell/function[f]", "?head=FUNCTION|lib:f")

        chkSyms("import a:lib.{k}; query q() = a.k;", *aDef, *lib, *kRef, *aRef, *kRef)
        chkSyms("import a:lib.{f}; query q() = a.f();", *aDef, *lib, *fRef, *aRef, *fRef)
        chkSyms("import a:lib.{k,f}; query q() = a.k;", *aDef, *lib, *kRef, *fRef, *aRef, *kRef)
        chkSyms("import a:lib.{k,f}; query q() = a.f();", *aDef, *lib, *kRef, *fRef, *aRef, *fRef)
    }

    @Test fun testImportExactInnerAlias() {
        file("lib/f1.rell", "val k = 123;")
        file("lib/f2.rell", "function f() = 456;")

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|lib/f1.rell", "?head=MODULE|lib")
        val kRef = arrayOf("k=DEF_CONSTANT|-|lib/f1.rell/constant[k]", "?head=CONSTANT|lib:k")
        val fRef = arrayOf("f=DEF_FUNCTION|-|lib/f2.rell/function[f]", "?head=FUNCTION|lib:f")

        val xDef = arrayOf("x=DEF_CONSTANT|import[x]|-", "?doc=IMPORT|:x|<import> [lib].{x: [k]}")
        val xRef = arrayOf("x=DEF_CONSTANT|-|main.rell/import[x]", "?doc=IMPORT|:x|<import> [lib].{x: [k]}")
        val yDef = arrayOf("y=DEF_FUNCTION|import[y]|-", "?doc=IMPORT|:y|<import> [lib].{y: [f]}")
        val yRef = arrayOf("y=DEF_FUNCTION|-|main.rell/import[y]", "?doc=IMPORT|:y|<import> [lib].{y: [f]}")
        chkSyms("import lib.{x:k}; query q() = x;", *lib, *xDef, *kRef, *xRef)
        chkSyms("import lib.{y:f}; query q() = y();", *lib, *yDef, *fRef, *yRef)
        chkSyms("import lib.{x:k,y:f}; query q() = x;", *lib, *xDef, *kRef, *yDef, *fRef, *xRef)
        chkSyms("import lib.{x:k,y:f}; query q() = y();", *lib, *xDef, *kRef, *yDef, *fRef, *yRef)

        val aDef = arrayOf("a=DEF_NAMESPACE|namespace[a]|-", "?doc=IMPORT|:a|<import> a: [lib].{...}")
        val aRef = arrayOf("a=DEF_NAMESPACE|-|main.rell/namespace[a]", "?head=IMPORT|:a")
        val axDef = arrayOf("x=DEF_CONSTANT|import[a.x]|-", "?doc=IMPORT|:a.x|<import> a: [lib].{x: [k]}")
        val axRef = arrayOf("x=DEF_CONSTANT|-|main.rell/import[a.x]", "?head=IMPORT|:a.x")
        val ayDef = arrayOf("y=DEF_FUNCTION|import[a.y]|-", "?doc=IMPORT|:a.y|<import> a: [lib].{y: [f]}")
        val ayRef = arrayOf("y=DEF_FUNCTION|-|main.rell/import[a.y]", "?head=IMPORT|:a.y")
        chkSyms("import a:lib.{x:k}; query q() = a.x;", *aDef, *lib, *axDef, *kRef, *aRef, *axRef)
        chkSyms("import a:lib.{y:f}; query q() = a.y();", *aDef, *lib, *ayDef, *fRef, *aRef, *ayRef)
        chkSyms("import a:lib.{x:k,y:f}; query q() = a.x;", *aDef, *lib, *axDef, *kRef, *ayDef, *fRef, *aRef, *axRef)
        chkSyms("import a:lib.{x:k,y:f}; query q() = a.y();", *aDef, *lib, *axDef, *kRef, *ayDef, *fRef, *aRef, *ayRef)
    }

    @Test fun testImportExactNamespace() {
        file("lib.rell", "module; namespace a { namespace b { val k = 123; } }")

        chkSymsFile("lib.rell",
            "a=DEF_NAMESPACE|namespace[a]|-", "?head=NAMESPACE|lib:a",
            "b=DEF_NAMESPACE|namespace[a.b]|-", "?head=NAMESPACE|lib:a.b",
            "k=DEF_CONSTANT|constant[a.b.k]|-", "?head=CONSTANT|lib:a.b.k",
        )

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib")
        val aRef = arrayOf("a=DEF_NAMESPACE|-|lib.rell/namespace[a]", "?head=NAMESPACE|lib:a")
        val bRef = arrayOf("b=DEF_NAMESPACE|-|lib.rell/namespace[a.b]", "?head=NAMESPACE|lib:a.b")
        val kRef = arrayOf("k=DEF_CONSTANT|-|lib.rell/constant[a.b.k]", "?head=CONSTANT|lib:a.b.k")

        chkSyms("import lib.{a}; query q() = a.b.k;", *lib, *aRef, *aRef, *bRef, *kRef)
        chkSyms("import lib.{a.b}; query q() = b.k;", *lib, *aRef, *bRef, *bRef, *kRef)
        chkSyms("import lib.{a.b.k}; query q() = k;", *lib, *aRef, *bRef, *kRef, *kRef)
    }

    @Test fun testImportExactWildcard() {
        file("lib.rell", "module; namespace a { namespace b { val k = 123; } }")

        chkSymsFile("lib.rell",
            "a=DEF_NAMESPACE|namespace[a]|-", "?head=NAMESPACE|lib:a",
            "b=DEF_NAMESPACE|namespace[a.b]|-", "?head=NAMESPACE|lib:a.b",
            "k=DEF_CONSTANT|constant[a.b.k]|-", "?head=CONSTANT|lib:a.b.k",
        )

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib")
        val aRef = arrayOf("a=DEF_NAMESPACE|-|lib.rell/namespace[a]", "?head=NAMESPACE|lib:a")
        val bRef = arrayOf("b=DEF_NAMESPACE|-|lib.rell/namespace[a.b]", "?head=NAMESPACE|lib:a.b")
        val kRef = arrayOf("k=DEF_CONSTANT|-|lib.rell/constant[a.b.k]", "?head=CONSTANT|lib:a.b.k")

        chkSyms("import lib.{a.*}; query q() = b.k;", *lib, *aRef, *bRef, *kRef)
        chkSyms("import lib.{a.b.*}; query q() = k;", *lib, *aRef, *bRef, *kRef)
    }

    @Test fun testImportExactWildcardAlias() {
        file("lib.rell", "module; namespace a { namespace b { val k = 123; } }")

        chkSymsFile("lib.rell",
            "a=DEF_NAMESPACE|namespace[a]|-", "?head=NAMESPACE|lib:a",
            "b=DEF_NAMESPACE|namespace[a.b]|-", "?head=NAMESPACE|lib:a.b",
            "k=DEF_CONSTANT|constant[a.b.k]|-", "?head=CONSTANT|lib:a.b.k",
        )

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib")
        val aRef = arrayOf("a=DEF_NAMESPACE|-|lib.rell/namespace[a]", "?head=NAMESPACE|lib:a")
        val abRef = arrayOf("b=DEF_NAMESPACE|-|lib.rell/namespace[a.b]", "?head=NAMESPACE|lib:a.b")
        val kRef = arrayOf("k=DEF_CONSTANT|-|lib.rell/constant[a.b.k]", "?head=CONSTANT|lib:a.b.k")

        val nsRef = arrayOf("ns=DEF_NAMESPACE|-|main.rell/namespace[ns]", "?head=IMPORT|:ns")
        val fooDef = arrayOf("foo=DEF_NAMESPACE|namespace[foo]|-", "?doc=IMPORT|:foo|<import> foo: [lib].{...}")
        val fooRef = arrayOf("foo=DEF_NAMESPACE|-|main.rell/namespace[foo]", "?doc=IMPORT|:foo|<import> foo: [lib].{...}")
        val fooNsRef = arrayOf("ns=DEF_NAMESPACE|-|main.rell/namespace[foo.ns]", "?head=IMPORT|:foo.ns")

        chkSyms("import lib.{ns:a.*}; query q() = ns.b.k;",
            *lib,
            "ns=DEF_NAMESPACE|namespace[ns]|-", "?doc=IMPORT|:ns|<import> [lib].{ns: [a].*}",
            *aRef, *nsRef, *abRef, *kRef,
        )

        chkSyms("import foo:lib.{ns:a.*}; query q() = foo.ns.b.k;",
            *fooDef, *lib,
            "ns=DEF_NAMESPACE|namespace[foo.ns]|-", "?doc=IMPORT|:foo.ns|<import> foo: [lib].{ns: [a].*}",
            *aRef, *fooRef, *fooNsRef, *abRef, *kRef,
        )

        chkSyms("import lib.{ns:a.b.*}; query q() = ns.k;",
            *lib,
            "ns=DEF_NAMESPACE|namespace[ns]|-", "?doc=IMPORT|:ns|<import> [lib].{ns: [a.b].*}",
            *aRef, *abRef, *nsRef, *kRef,
        )

        chkSyms("import foo:lib.{ns:a.b.*}; query q() = foo.ns.k;",
            *fooDef, *lib,
            "ns=DEF_NAMESPACE|namespace[foo.ns]|-", "?doc=IMPORT|:foo.ns|<import> foo: [lib].{ns: [a.b].*}",
            *aRef, *abRef, *fooRef, *fooNsRef, *kRef,
        )
    }

    @Test fun testImportExactReimport() {
        file("sub.rell", "module; import lib.{f}; val k = 123;")
        file("lib.rell", "module; import sub.{k}; function f() = 456;")

        chkSymsFile("sub.rell",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f",
            "k=DEF_CONSTANT|constant[k]|-", "?head=CONSTANT|sub:k",
        )

        chkSymsFile("lib.rell",
            "sub=DEF_IMPORT_MODULE|-|sub.rell", "?head=MODULE|sub",
            "k=DEF_CONSTANT|-|sub.rell/constant[k]", "?head=CONSTANT|sub:k",
            "f=DEF_FUNCTION|function[f]|-", "?head=FUNCTION|lib:f",
        )

        chkSyms("import lib.{k}; query q() = k;",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "k=DEF_CONSTANT|-|sub.rell/constant[k]", "?head=CONSTANT|sub:k",
            "k=DEF_CONSTANT|-|sub.rell/constant[k]", "?head=CONSTANT|sub:k",
        )

        chkSyms("import sub.{f}; query q() = f();",
            "sub=DEF_IMPORT_MODULE|-|sub.rell", "?head=MODULE|sub",
            "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f",
            "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f",
        )
    }

    @Test fun testImportExactPath() {
        file("lib.rell", "module; namespace foo { namespace bar { val k = 123; } }")

        chkSymsFile("lib.rell",
            "foo=DEF_NAMESPACE|namespace[foo]|-", "?head=NAMESPACE|lib:foo",
            "bar=DEF_NAMESPACE|namespace[foo.bar]|-", "?head=NAMESPACE|lib:foo.bar",
            "k=DEF_CONSTANT|constant[foo.bar.k]|-", "?head=CONSTANT|lib:foo.bar.k",
        )

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib")
        val foo = arrayOf("foo=DEF_NAMESPACE|-|lib.rell/namespace[foo]", "?head=NAMESPACE|lib:foo")
        val bar = arrayOf("bar=DEF_NAMESPACE|-|lib.rell/namespace[foo.bar]", "?head=NAMESPACE|lib:foo.bar")
        val kRef = arrayOf("k=DEF_CONSTANT|-|lib.rell/constant[foo.bar.k]", "?head=CONSTANT|lib:foo.bar.k")

        chkSyms("import lib.{foo.bar.k}; query q() = k;", *lib, *foo, *bar, *kRef, *kRef)

        chkSyms("import lib.{a: foo.bar.k}; query q() = a;",
            *lib,
            "a=DEF_CONSTANT|import[a]|-", "?doc=IMPORT|:a|<import> [lib].{a: [foo.bar.k]}",
            *foo, *bar, *kRef,
            "a=DEF_CONSTANT|-|main.rell/import[a]", "?head=IMPORT|:a",
        )
    }

    @Test fun testImportExactChain() {
        file("m1.rell", "module; namespace ns1 { import m2; }")
        file("m2.rell", "module; namespace ns2 { import m3.{ns3}; }")
        file("m3.rell", "module; namespace ns3 { val k = 123; }")

        chkSymsFile("m1.rell",
            "ns1=DEF_NAMESPACE|namespace[ns1]|-", "?head=NAMESPACE|m1:ns1",
            "m2=DEF_IMPORT_MODULE|import[ns1.m2]|m2.rell", "?head=MODULE|m2",
        )

        chkSymsFile("m2.rell",
            "ns2=DEF_NAMESPACE|namespace[ns2]|-", "?head=NAMESPACE|m2:ns2",
            "m3=DEF_IMPORT_MODULE|-|m3.rell", "?head=MODULE|m3",
            "ns3=DEF_NAMESPACE|-|m3.rell/namespace[ns3]", "?head=NAMESPACE|m3:ns3",
        )

        chkSymsFile("m3.rell",
            "ns3=DEF_NAMESPACE|namespace[ns3]|-", "?head=NAMESPACE|m3:ns3",
            "k=DEF_CONSTANT|constant[ns3.k]|-", "?head=CONSTANT|m3:ns3.k",
        )

        chkSyms("import m1.{ns1.m2.ns2.ns3.k}; query q() = k;",
            "m1=DEF_IMPORT_MODULE|-|m1.rell", "?head=MODULE|m1",
            "ns1=DEF_NAMESPACE|-|m1.rell/namespace[ns1]", "?head=NAMESPACE|m1:ns1",
            "m2=DEF_IMPORT_MODULE|-|m1.rell/import[ns1.m2]", "?doc=IMPORT|m1:ns1.m2|<import> [m2]",
            "ns2=DEF_NAMESPACE|-|m2.rell/namespace[ns2]", "?head=NAMESPACE|m2:ns2",
            "ns3=DEF_NAMESPACE|-|m3.rell/namespace[ns3]", "?head=NAMESPACE|m3:ns3",
            "k=DEF_CONSTANT|-|m3.rell/constant[ns3.k]", "?head=CONSTANT|m3:ns3.k",
            "k=DEF_CONSTANT|-|m3.rell/constant[ns3.k]", "?head=CONSTANT|m3:ns3.k",
        )
    }

    @Test fun testImportExactDef() {
        initImportExact()

        chkSyms("import k: lib.{data, state, f};",
            "k=DEF_NAMESPACE|namespace[k]|-", "?doc=IMPORT|:k|<import> k: [lib].{...}",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|data",
            "state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|state",
            "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f",
        )

        chkSyms("import lib.{p: data, q: state, r: f};",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "p=DEF_ENTITY|import[p]|-", "?doc=IMPORT|:p|<import> [lib].{p: [data]}",
            "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|data",
            "q=DEF_OBJECT|import[q]|-", "?doc=IMPORT|:q|<import> [lib].{q: [state]}",
            "state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|state",
            "r=DEF_FUNCTION|import[r]|-", "?doc=IMPORT|:r|<import> [lib].{r: [f]}",
            "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f",
        )

        chkSyms("import lib.{ns.p};",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns",
            "p=DEF_STRUCT|-|lib.rell/struct[ns.p]", "?head=STRUCT|lib:ns.p",
        )
    }

    @Test fun testImportExactRef() {
        initImportExact()
        file("module.rell", """
            import lib.{data1:data, state1:state, f1:f};
            import lib.{data2:data};
            import lib.{state2:state};
            import lib.{f2:f};
            import a: lib.{data, state, f};
            import b: lib.{data3:data, state3:state, f3:f};
        """)

        chkSymsExpr("create data1('bob')",
            "data1=DEF_ENTITY|-|module.rell/import[data1]", "?doc=IMPORT|:data1|<import> [lib].{data1: [data]}",
        )
        chkSymsExpr("state1.x",
            "state1=DEF_OBJECT|-|module.rell/import[state1]", "?doc=IMPORT|:state1|<import> [lib].{state1: [state]}",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkSymsExpr("f1()", "f1=DEF_FUNCTION|-|module.rell/import[f1]", "?doc=IMPORT|:f1|<import> [lib].{f1: [f]}")

        chkSymsExpr("create data2('bob')",
            "data2=DEF_ENTITY|-|module.rell/import[data2]", "?doc=IMPORT|:data2|<import> [lib].{data2: [data]}",
        )
        chkSymsExpr("state2.x",
            "state2=DEF_OBJECT|-|module.rell/import[state2]", "?doc=IMPORT|:state2|<import> [lib].{state2: [state]}",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkSymsExpr("f2()", "f2=DEF_FUNCTION|-|module.rell/import[f2]", "?doc=IMPORT|:f2|<import> [lib].{f2: [f]}")

        val aRef = arrayOf("a=DEF_NAMESPACE|-|module.rell/namespace[a]", "?doc=IMPORT|:a|<import> a: [lib].{...}")
        chkSymsExpr("create a.data('bob')", *aRef, "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|data")
        chkSymsExpr("a.state.x", *aRef,
            "state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|state",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkSymsExpr("a.f()", *aRef, "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f")

        val bRef = arrayOf("b=DEF_NAMESPACE|-|module.rell/namespace[b]", "?doc=IMPORT|:b|<import> b: [lib].{...}")
        chkSymsExpr("create b.data3('bob')", *bRef,
            "data3=DEF_ENTITY|-|module.rell/import[b.data3]", "?doc=IMPORT|:b.data3|<import> b: [lib].{data3: [data]}",
        )
        chkSymsExpr("b.state3.x", *bRef,
            "state3=DEF_OBJECT|-|module.rell/import[b.state3]", "?doc=IMPORT|:b.state3|<import> b: [lib].{state3: [state]}",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkSymsExpr("b.f3()", *bRef,
            "f3=DEF_FUNCTION|-|module.rell/import[b.f3]", "?doc=IMPORT|:b.f3|<import> b: [lib].{f3: [f]}",
        )
    }

    private fun initImportExact() {
        file("lib.rell", """
            module;
            entity data { name; }
            object state { x: integer = 1; }
            function f() = 123;
            namespace ns { struct p {} }
        """)
    }

    @Test fun testImportExactConflict() {
        file("lib.rell", "module; function f(): integer = 123;")

        chkSyms("function f(): integer = 123; import lib.{f};",
            "f=DEF_FUNCTION|function[f]|-", "?head=FUNCTION|:f",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "f=UNKNOWN|-|-", "?head=-",
            err = "[name_conflict:user:f:IMPORT:main.rell(1:42)][name_conflict:user:f:FUNCTION:main.rell(1:10)]",
        )
    }

    @Test fun testImportModuleDef() {
        file("lib.rell", "module; namespace a { struct s {} }")
        file("dir/module.rell", "module;")
        file("dir/sub.rell", "module;")

        chkSyms("import lib; struct z { x: lib.a.s; }",
            "lib=DEF_IMPORT_MODULE|import[lib]|lib.rell", "?head=MODULE|lib",
            "lib=DEF_IMPORT_MODULE|-|main.rell/import[lib]", "?doc=IMPORT|:lib|<import> [lib]",
            "a=DEF_NAMESPACE|-|lib.rell/namespace[a]", "?head=NAMESPACE|lib:a",
            "s=DEF_STRUCT|-|lib.rell/struct[a.s]", "?head=STRUCT|lib:a.s",
        )

        chkSyms("import bil: lib; struct z { x: bil.a.s; }",
            "bil=DEF_IMPORT_ALIAS|import[bil]|-", "?doc=IMPORT|:bil|<import> bil: [lib]",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "bil=EXPR_IMPORT_ALIAS|-|main.rell/import[bil]", "?doc=IMPORT|:bil|<import> bil: [lib]",
            "a=DEF_NAMESPACE|-|lib.rell/namespace[a]", "?head=NAMESPACE|lib:a",
            "s=DEF_STRUCT|-|lib.rell/struct[a.s]", "?head=STRUCT|lib:a.s",
        )

        chkSyms("import foo;", "foo=UNKNOWN|-|-", "?head=-", err = "import:not_found:foo")
        chkSyms("import foo.bar;", "foo=UNKNOWN|-|-", "?head=-", "bar=UNKNOWN|-|-", "?head=-",
            err = "import:not_found:foo.bar")

        chkSyms("import dir;", "dir=DEF_IMPORT_MODULE|import[dir]|dir/module.rell", "?head=MODULE|dir")
        chkSyms("import dir.sub;",
            "dir=DEF_IMPORT_MODULE|-|dir/module.rell", "?head=MODULE|dir",
            "sub=DEF_IMPORT_MODULE|import[sub]|dir/sub.rell", "?head=MODULE|dir.sub",
        )

        val dirImp = arrayOf("dir=DEF_IMPORT_MODULE|-|dir/module.rell", "?head=MODULE|dir")
        chkSyms("import dir.foo;", *dirImp, "foo=UNKNOWN|-|-", "?head=-", err = "import:not_found:dir.foo")
        chkSyms("import dir.sub.foo;", *dirImp,
            "sub=DEF_IMPORT_MODULE|-|dir/sub.rell", "?head=MODULE|dir.sub",
            "foo=UNKNOWN|-|-", "?head=-",
            err = "import:not_found:dir.sub.foo",
        )
    }

    @Test fun testImportModuleRef() {
        file("lib.rell", "module; namespace a { struct s {} }")
        file("dir/module.rell", "module;")
        file("dir/sub.rell", "module; namespace b { struct p {} }")
        file("module.rell", """
            import lib;
            import bil: lib;
            import dir.sub;
            import bus: dir.sub;
        """)

        val aRef = arrayOf("a=DEF_NAMESPACE|-|lib.rell/namespace[a]", "?head=NAMESPACE|lib:a")
        chkSymsExpr("lib.a.s()",
            "lib=DEF_IMPORT_MODULE|-|module.rell/import[lib]", "?doc=IMPORT|:lib|<import> [lib]",
            *aRef,
            "s=DEF_STRUCT|-|lib.rell/struct[a.s]", "?head=STRUCT|lib:a.s",
        )
        chkSymsExpr("bil.a.s()",
            "bil=EXPR_IMPORT_ALIAS|-|module.rell/import[bil]", "?doc=IMPORT|:bil|<import> bil: [lib]",
            *aRef,
            "s=DEF_STRUCT|-|lib.rell/struct[a.s]", "?head=STRUCT|lib:a.s",
        )

        val bRef = arrayOf("b=DEF_NAMESPACE|-|dir/sub.rell/namespace[b]", "?head=NAMESPACE|dir.sub:b")
        chkSymsExpr("sub.b.p()",
            "sub=DEF_IMPORT_MODULE|-|module.rell/import[sub]",
            *bRef,
            "p=DEF_STRUCT|-|dir/sub.rell/struct[b.p]", "?head=STRUCT|dir.sub:b.p",
        )
        chkSymsExpr("bus.b.p()",
            "bus=EXPR_IMPORT_ALIAS|-|module.rell/import[bus]", "?doc=IMPORT|:bus|<import> bus: [dir.sub]",
            *bRef,
            "p=DEF_STRUCT|-|dir/sub.rell/struct[b.p]", "?head=STRUCT|dir.sub:b.p",
        )
    }

    @Test fun testImportModuleAlias() {
        file("lib.rell", "module; val k = 123;")
        chkSyms("import ref: lib; query q() = ref.k;",
            "ref=DEF_IMPORT_ALIAS|import[ref]|-", "?doc=IMPORT|:ref|<import> ref: [lib]",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "ref=EXPR_IMPORT_ALIAS|-|main.rell/import[ref]", "?doc=IMPORT|:ref|<import> ref: [lib]",
            "k=DEF_CONSTANT|-|lib.rell/constant[k]", "?head=CONSTANT|lib:k",
        )
    }

    @Test fun testImportModuleFile() {
        file("lib.rell", "module; val k = 123;")
        chkSyms("import lib; query q() = lib.k;",
            "lib=DEF_IMPORT_MODULE|import[lib]|lib.rell", "?head=MODULE|lib",
            "lib=DEF_IMPORT_MODULE|-|main.rell/import[lib]", "?head=IMPORT|:lib",
            "k=DEF_CONSTANT|-|lib.rell/constant[k]", "?head=CONSTANT|lib:k",
        )
    }

    @Test fun testImportModuleDirMain() {
        file("mod/top.rell", "struct t {}")
        file("mod/module.rell", "struct u {}")
        file("mod/bot.rell", "val k = 123;")
        chkSyms("import mod; query q() = mod.k;",
            "mod=DEF_IMPORT_MODULE|import[mod]|mod/module.rell", "?head=MODULE|mod",
            "mod=DEF_IMPORT_MODULE|-|main.rell/import[mod]", "?head=IMPORT|:mod",
            "k=DEF_CONSTANT|-|mod/bot.rell/constant[k]", "?head=CONSTANT|mod:k",
        )
    }

    @Test fun testImportModuleDirNoMain() {
        file("dir/a.rell", "function f() = 123;")
        file("dir/b.rell", "function g() {}")
        chkSyms("import dir; query q() = dir.f();",
            "dir=DEF_IMPORT_MODULE|import[dir]|dir/a.rell", "?head=MODULE|dir",
            "dir=DEF_IMPORT_MODULE|-|main.rell/import[dir]", "?head=IMPORT|:dir",
            "f=DEF_FUNCTION|-|dir/a.rell/function[f]", "?head=FUNCTION|dir:f",
        )
    }

    @Test fun testImportModuleNested() {
        file("a/b/c.rell", "module; val k = 123;")
        file("x/module.rell", "module;")
        file("x/y/z.rell", "module; function f() = 456;")

        val ab = arrayOf("a=DEF_IMPORT_MODULE|-|-", "?head=-", "b=DEF_IMPORT_MODULE|-|-", "?head=-")
        val cDef = arrayOf("c=DEF_IMPORT_MODULE|import[c]|a/b/c.rell", "?head=MODULE|a.b.c")
        val cRef = arrayOf("c=DEF_IMPORT_MODULE|-|main.rell/import[c]", "?head=IMPORT|:c")
        val cMod = arrayOf("c=DEF_IMPORT_MODULE|-|a/b/c.rell", "?head=MODULE|a.b.c")
        val kRef = arrayOf("k=DEF_CONSTANT|-|a/b/c.rell/constant[k]", "?head=CONSTANT|a.b.c:k")
        chkSyms("import a.b.c; query q() = c.k;", *ab, *cDef, *cRef, *kRef)
        chkSyms("import a.b.c.*; query q() = k;", *ab, *cMod, *kRef)
        chkSyms("import a.b.c.{k}; query q() = k;", *ab, *cMod, *kRef, *kRef)

        val xy = arrayOf("x=DEF_IMPORT_MODULE|-|x/module.rell", "?head=MODULE|x", "y=DEF_IMPORT_MODULE|-|-", "?head=-")
        val zDef = arrayOf("z=DEF_IMPORT_MODULE|import[z]|x/y/z.rell", "?head=MODULE|x.y.z")
        val zRef = arrayOf("z=DEF_IMPORT_MODULE|-|main.rell/import[z]", "?head=IMPORT|:z")
        val zMod = arrayOf("z=DEF_IMPORT_MODULE|-|x/y/z.rell", "?head=MODULE|x.y.z")
        val fRef = "f=DEF_FUNCTION|-|x/y/z.rell/function[f]"
        chkSyms("import x.y.z; query q() = z.f();", *xy, *zDef, *zRef, fRef)
        chkSyms("import x.y.z.*; query q() = f();", *xy, *zMod, fRef)
        chkSyms("import x.y.z.{f}; query q() = f();", *xy, *zMod, fRef, fRef)
    }

    @Test fun testImportModuleNotExists() {
        file("a/module.rell", "module;")
        file("a/b/c/module.rell", "module;")

        chkSyms("import a.b.c.d;",
            "a=DEF_IMPORT_MODULE|-|a/module.rell", "?head=MODULE|a",
            "b=DEF_IMPORT_MODULE|-|-", "?head=-",
            "c=DEF_IMPORT_MODULE|-|a/b/c/module.rell", "?head=MODULE|a.b.c",
            "d=UNKNOWN|-|-", "?head=-",
            err = "import:not_found:a.b.c.d",
        )

        chkSyms("import a.b.c.d.e;",
            "a=DEF_IMPORT_MODULE|-|a/module.rell", "?head=MODULE|a",
            "b=DEF_IMPORT_MODULE|-|-", "?head=-",
            "c=DEF_IMPORT_MODULE|-|a/b/c/module.rell", "?head=MODULE|a.b.c",
            "d=UNKNOWN|-|-", "?head=-",
            "e=UNKNOWN|-|-", "?head=-",
            err = "import:not_found:a.b.c.d.e",
        )
    }

    @Test fun testImportExactComplex() {
        initImportExactComplex()

        val ns = arrayOf("ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns")
        val sub = arrayOf("sub=DEF_IMPORT_MODULE|-|lib.rell/import[sub]", "?doc=IMPORT|lib:sub|<import> [sub]")
        val subns = arrayOf("subns=DEF_NAMESPACE|-|sub.rell/namespace[subns]", "?head=NAMESPACE|sub:subns")
        val s1 = arrayOf("s1=DEF_STRUCT|-|sub.rell/struct[s1]", "?head=STRUCT|sub:s1")
        val s2 = arrayOf("s2=DEF_STRUCT|-|sub.rell/struct[subns.s2]", "?head=STRUCT|sub:subns.s2")

        chkImportExactComplex("sub", *sub)
        chkImportExactComplex("bus", "bus=EXPR_IMPORT_ALIAS|-|lib.rell/import[bus]", "?doc=IMPORT|lib:bus|<import> bus: [sub]")
        chkImportExactComplex("sub.s1", *sub, *s1)
        chkImportExactComplex("bus.s1", "bus=EXPR_IMPORT_ALIAS|-|lib.rell/import[bus]", "?head=IMPORT|lib:bus", *s1)
        chkImportExactComplex("sub.subns", *sub, *subns)
        chkImportExactComplex("bus.subns", "bus=EXPR_IMPORT_ALIAS|-|lib.rell/import[bus]", "?head=IMPORT|lib:bus", *subns)
        chkImportExactComplex("sub.subns.s2", *sub, *subns, *s2)
        chkImportExactComplex("bus.subns.s2",
            "bus=EXPR_IMPORT_ALIAS|-|lib.rell/import[bus]", "?head=IMPORT|lib:bus",
            *subns, *s2,
        )

        chkImportExactComplex("data", "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|data")
        chkImportExactComplex("f", "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f")
        chkImportExactComplex("ns", *ns)
        chkImportExactComplex("ns.p", *ns, "p=DEF_STRUCT|-|lib.rell/struct[ns.p]", "?head=STRUCT|lib:ns.p")

        val nsSub = arrayOf("sub=DEF_IMPORT_MODULE|-|lib.rell/import[ns.sub]", "?head=IMPORT|lib:ns.sub")
        val nsBus = arrayOf("bus=EXPR_IMPORT_ALIAS|-|lib.rell/import[ns.bus]", "?head=IMPORT|lib:ns.bus")
        chkImportExactComplex("ns.sub", *ns, *nsSub)
        chkImportExactComplex("ns.bus", *ns, *nsBus)
        chkImportExactComplex("ns.sub.s1", *ns, *nsSub, *s1)
        chkImportExactComplex("ns.bus.s1", *ns, *nsBus, *s1)
        chkImportExactComplex("ns.sub.subns", *ns, *nsSub, *subns)
        chkImportExactComplex("ns.bus.subns", *ns, *nsBus, *subns)
        chkImportExactComplex("ns.sub.subns.s2", *ns, *nsSub, *subns, *s2)
        chkImportExactComplex("ns.bus.subns.s2", *ns, *nsBus, *subns, *s2)
    }

    @Test fun testImportExactComplexUnknown() {
        initImportExactComplex()

        val ns = arrayOf("ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns")
        val subns = arrayOf("subns=DEF_NAMESPACE|-|sub.rell/namespace[subns]", "?head=NAMESPACE|sub:subns")
        val sub = arrayOf("sub=DEF_IMPORT_MODULE|-|lib.rell/import[sub]", "?head=IMPORT|lib:sub")
        val bus = arrayOf("bus=EXPR_IMPORT_ALIAS|-|lib.rell/import[bus]", "?head=IMPORT|lib:bus")

        chkImportExactComplexErr("Z", "Z=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("ns.Z", *ns, "Z=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("sub.Z", *sub, "Z=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("bus.Z", *bus, "Z=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("sub.subns.Z", *sub, *subns, "Z=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("bus.subns.Z", *bus, *subns, "Z=UNKNOWN|-|-", "?head=-")

        val nsSub = arrayOf("sub=DEF_IMPORT_MODULE|-|lib.rell/import[ns.sub]", "?head=IMPORT|lib:ns.sub")
        val nsBus = arrayOf("bus=EXPR_IMPORT_ALIAS|-|lib.rell/import[ns.bus]", "?head=IMPORT|lib:ns.bus")
        chkImportExactComplexErr("ns.sub.Z", *ns, *nsSub, "Z=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("ns.bus.Z", *ns, *nsBus, "Z=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("ns.sub.subns.Z", *ns, *nsSub, *subns, "Z=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("ns.bus.subns.Z", *ns, *nsBus, *subns, "Z=UNKNOWN|-|-", "?head=-")

        val zyx = arrayOf("Z=UNKNOWN|-|-", "?head=-", "Y=UNKNOWN|-|-", "?head=-", "X=UNKNOWN|-|-", "?head=-")
        chkImportExactComplexErr("Z.Y.X", *zyx)
        chkImportExactComplexErr("sub.Z.Y.X", *sub, *zyx)
        chkImportExactComplexErr("sub.subns.Z.Y.X", *sub, *subns, *zyx)
        chkImportExactComplexErr("ns.sub.Z.Y.X", *ns, *nsSub, *zyx)
        chkImportExactComplexErr("ns.sub.subns.Z.Y.X", *ns, *nsSub, *subns, *zyx)
    }

    private fun initImportExactComplex() {
        file("lib.rell", """
            module;
            import sub;
            import bus: sub;
            entity data { name; }
            function f() = 123;
            namespace ns {
                import sub;
                import bus: sub;
                struct p {}
            }
        """)
        file("sub.rell", """
            module;
            struct s1 {}
            namespace subns {
                struct s2 {}
            }
        """)
    }

    private fun chkImportExactComplex(name: String, vararg expected: String, err: String? = null) {
        chkSyms("import lib.{$name};", "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib", *expected, err = err)

        val expAlias = replaceSymInfo(expected.takeLast(2).first(), name = "x", defId = "import[x]", link = "-")
        val expAliasDoc = "?doc=IMPORT|:x|<import> [lib].{x: [$name]}"
        chkSyms("import lib.{x: $name};",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            expAlias, expAliasDoc,
            *expected,
            err = err,
        )
    }

    private fun chkImportExactComplexErr(name: String, vararg expected: String) {
        chkImportExactComplex(name, *expected, err = "import:name_unknown:Z")
    }

    @Test fun testImportWildcard() {
        file("lib.rell", "module; val k = 123;")
        chkSyms("import lib.*; query q() = k;",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "k=DEF_CONSTANT|-|lib.rell/constant[k]", "?head=CONSTANT|lib:k",
        )
    }

    @Test fun testImportWildcardAlias() {
        file("lib.rell", "module; val k = 123;")
        chkSyms("import ns: lib.*; query q() = ns.k;",
            "ns=DEF_NAMESPACE|namespace[ns]|-", "?doc=IMPORT|:ns|<import> ns: [lib].*",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "ns=DEF_NAMESPACE|-|main.rell/namespace[ns]", "?doc=IMPORT|:ns|<import> ns: [lib].*",
            "k=DEF_CONSTANT|-|lib.rell/constant[k]", "?head=CONSTANT|lib:k",
        )
    }

    @Test fun testImportWildcardDef() {
        file("lib.rell", """
            module;
            entity data { name; }
            object state { x: integer = 1; }
            function f() = 123;
            namespace ns { struct rec {} }
        """)

        val kDef = "k=DEF_NAMESPACE|namespace[k]|-"
        chkSyms("import k: lib.*;",
            kDef, "?doc=IMPORT|:k|<import> k: [lib].*",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
        )
        chkSyms("import k: lib.{ns.*};",
            kDef, "?doc=IMPORT|:k|<import> k: [lib].{...}",
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns",
        )
    }

    @Test fun testImportWildcardRef() {
        file("lib.rell", """
            module;
            entity data { name; }
            object state { x: integer = 1; }
            function f() = 123;
        """)
        file("module.rell", """
            namespace a { import lib.*; }
            import b: lib.*;
        """)

        val aRef = arrayOf("a=DEF_NAMESPACE|-|module.rell/namespace[a]", "?head=NAMESPACE|:a")
        chkSymsExpr("create a.data('bob')", *aRef, "data=DEF_ENTITY|-|lib.rell/entity[data]")
        chkSymsExpr("a.state.x", *aRef,
            "state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|state",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkSymsExpr("a.f()", *aRef, "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f")

        val bRef = arrayOf("b=DEF_NAMESPACE|-|module.rell/namespace[b]", "?doc=IMPORT|:b|<import> b: [lib].*")
        chkSymsExpr("create b.data('bob')", *bRef, "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|data")
        chkSymsExpr("b.state.x", *bRef,
            "state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|state",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkSymsExpr("b.f()", *bRef, "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f")
    }

    @Test fun testImportAllKindsModule() {
        initImportAllKinds()
        file("module.rell", "import lib;")

        val lib = arrayOf("lib=DEF_IMPORT_MODULE|-|module.rell/import[lib]", "?head=IMPORT|:lib")

        chkSymsExpr("create lib.data('Bob')", *lib, "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|data")
        chkSymsExpr("lib.rec(123)", *lib, "rec=DEF_STRUCT|-|lib.rell/struct[rec]", "?head=STRUCT|lib:rec")
        chkSymsExpr("lib.op()", *lib, "op=DEF_OPERATION|-|lib.rell/operation[op]", "?head=OPERATION|lib:op|op")
        chkSymsExpr("lib.q()", *lib, "q=DEF_QUERY|-|lib.rell/query[q]", "?head=QUERY|lib:q|q")
        chkSymsExpr("lib.f()", *lib, "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f")
        chkSymsExpr("lib.MAGIC", *lib, "MAGIC=DEF_CONSTANT|-|lib.rell/constant[MAGIC]", "?head=CONSTANT|lib:MAGIC")

        chkSymsExpr("lib.state.x", *lib,
            "state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|state",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkSymsExpr("lib.colors.red", *lib,
            "colors=DEF_ENUM|-|lib.rell/enum[colors]", "?head=ENUM|lib:colors",
            "red=MEM_ENUM_VALUE|-|lib.rell/enum[colors].value[red]", "?head=ENUM_VALUE|lib:colors.red",
        )
        chkSymsExpr("lib.ns.p()", *lib,
            "ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns",
            "p=DEF_STRUCT|-|lib.rell/struct[ns.p]", "?head=STRUCT|lib:ns.p",
        )
    }

    @Test fun testImportAllKindsExact() {
        initImportAllKinds()

        chkImportAllKindsExact("data", "create data('Bob')", "DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|data")
        chkImportAllKindsExact("rec", "rec(123)", "DEF_STRUCT|-|lib.rell/struct[rec]", "?head=STRUCT|lib:rec")
        chkImportAllKindsExact("op", "op()", "DEF_OPERATION|-|lib.rell/operation[op]", "?head=OPERATION|lib:op|op")
        chkImportAllKindsExact("q", "q()", "DEF_QUERY|-|lib.rell/query[q]", "?head=QUERY|lib:q|q")
        chkImportAllKindsExact("f", "f()", "DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f")
        chkImportAllKindsExact("MAGIC", "MAGIC", "DEF_CONSTANT|-|lib.rell/constant[MAGIC]", "?head=CONSTANT|lib:MAGIC")

        chkImportAllKindsExact("ns", "ns.p()",
            "DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns",
            "p=DEF_STRUCT|-|lib.rell/struct[ns.p]", "?head=STRUCT|lib:ns.p",
        )
        chkImportAllKindsExact("state", "state.x",
            "DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|state",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkImportAllKindsExact("colors", "colors.red",
            "DEF_ENUM|-|lib.rell/enum[colors]", "?head=ENUM|lib:colors",
            "red=MEM_ENUM_VALUE|-|lib.rell/enum[colors].value[red]", "?head=ENUM_VALUE|lib:colors.red",
        )
    }

    private fun chkImportAllKindsExact(
        name: String,
        expr: String,
        expHeadInfo: String,
        expHeadDoc: String,
        vararg expTail: String,
    ) {
        val code = "import lib.{$name}; function test() = $expr;"
        chkSyms(code,
            "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib",
            "$name=$expHeadInfo", expHeadDoc,
            "$name=$expHeadInfo", expHeadDoc,
            *expTail)
    }

    @Test fun testImportAllKindsWildcard() {
        initImportAllKinds()
        file("module.rell", "import lib.*;")

        chkSymsExpr("create data('Bob')", "data=DEF_ENTITY|-|lib.rell/entity[data]", "?head=ENTITY|lib:data|data")
        chkSymsExpr("rec(123)", "rec=DEF_STRUCT|-|lib.rell/struct[rec]", "?head=STRUCT|lib:rec")
        chkSymsExpr("op()", "op=DEF_OPERATION|-|lib.rell/operation[op]", "?head=OPERATION|lib:op|op")
        chkSymsExpr("q()", "q=DEF_QUERY|-|lib.rell/query[q]", "?head=QUERY|lib:q|q")
        chkSymsExpr("f()", "f=DEF_FUNCTION|-|lib.rell/function[f]", "?head=FUNCTION|lib:f")
        chkSymsExpr("MAGIC", "MAGIC=DEF_CONSTANT|-|lib.rell/constant[MAGIC]", "?head=CONSTANT|lib:MAGIC")

        chkSymsExpr("state.x",
            "state=DEF_OBJECT|-|lib.rell/object[state]", "?head=OBJECT|lib:state|state",
            "x=MEM_ENTITY_ATTR_NORMAL|-|lib.rell/object[state].attr[x]", "?head=OBJECT_ATTR|lib:state.x",
        )
        chkSymsExpr("colors.red",
            "colors=DEF_ENUM|-|lib.rell/enum[colors]", "?head=ENUM|lib:colors",
            "red=MEM_ENUM_VALUE|-|lib.rell/enum[colors].value[red]", "?head=ENUM_VALUE|lib:colors.red",
        )
        chkSymsExpr("ns.p()",
            "ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|lib:ns",
            "p=DEF_STRUCT|-|lib.rell/struct[ns.p]", "?head=STRUCT|lib:ns.p",
        )
    }

    private fun initImportAllKinds() {
        tst.testLib = true
        file("lib.rell", """
            module;
            entity data { name; }
            object state { x: integer = 1; }
            struct rec { x: integer; }
            enum colors { red, green, blue }
            operation op() {}
            query q() = 123;
            function f() = 123;
            val MAGIC = 123;
            namespace ns { struct p {} }
        """)
    }

    @Test fun testImportAnonymous() {
        file("lib.rell", "module;")
        chkSyms("import _: lib;", "_=UNKNOWN|-|-", "lib=DEF_IMPORT_MODULE|-|lib.rell", "?head=MODULE|lib")
    }

    @Test fun testNamespace() {
        file("lib.rell", """
            module;
            namespace foo.bar { enum color {} }
        """)

        val fooRef = arrayOf("foo=DEF_NAMESPACE|-|lib.rell/namespace[foo]", "?head=NAMESPACE|lib:foo")
        val barRef = arrayOf("bar=DEF_NAMESPACE|-|lib.rell/namespace[foo.bar]", "?head=NAMESPACE|lib:foo.bar")
        val colorRef = arrayOf("color=DEF_ENUM|-|lib.rell/enum[foo.bar.color]", "?head=ENUM|lib:foo.bar.color")

        chkSyms("import lib.*; val k: foo.bar.color? = null;", *fooRef, *barRef, *colorRef)
        chkSyms("import lib; val k: lib.foo.bar.color? = null;", *fooRef, *barRef, *colorRef)
        chkSyms("import lib.{foo}; val k: foo.bar.color? = null;", *fooRef, *fooRef, *barRef, *colorRef)
        chkSyms("import lib.{foo.bar}; val k: bar.color? = null;", *fooRef, *barRef, *barRef, *colorRef)
        chkSyms("import lib.{foo.bar.color}; val k: color? = null;", *fooRef, *barRef, *colorRef, *colorRef)
        chkSyms("import lib.{foo.bar.*}; val k: color? = null;", *fooRef, *barRef, *colorRef)
    }

    @Test fun testImportRootModule() {
        file("module.rell", "module; val X = 123;")
        file("lib.rell", "module; import a: ^; function f() = a.X;")
        chkSymsFile("lib.rell",
            "a=DEF_IMPORT_ALIAS|import[a]|-", "?doc=IMPORT|lib:a|<import> a: ['']",
            "a=EXPR_IMPORT_ALIAS|-|lib.rell/import[a]", "?doc=IMPORT|lib:a|<import> a: ['']",
        )
    }
}
