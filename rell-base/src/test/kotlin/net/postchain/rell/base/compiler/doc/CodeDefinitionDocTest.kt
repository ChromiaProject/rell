/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.doc

import net.postchain.rell.base.lmodel.dsl.BaseLTest
import org.junit.Test

class CodeDefinitionDocTest: BaseCodeDocTest() {
    @Test fun testModule() {
        file("lib.rell", "module;")
        file("mod_abs.rell", "abstract module;")
        file("mod_ext.rell", "@external module;")
        file("mod_test.rell", "@test module;")

        chkDoc("", "lib", "MODULE|lib", "<module>")
        chkDoc("", "mod_abs", "MODULE|mod_abs", "<abstract> <module>")
        chkDoc("", "mod_ext", "MODULE|mod_ext", "@external\n<module>")
        chkDoc("", "mod_test", "MODULE|mod_test", "@test\n<module>")
    }

    @Test fun testModuleMount() {
        file("a/module.rell", "module;")
        file("a/b/module.rell", "@mount('mod_mount') module;")
        file("a/b/c/module.rell", "module;")
        file("a/b/c/d/module.rell", "@mount('^.sub_mount') module;")

        chkDoc("", "a", "MODULE|a", "<module>")
        chkDoc("", "a.b", "MODULE|a.b|mod_mount", "@mount(\"mod_mount\")\n<module>")
        chkDoc("", "a.b.c", "MODULE|a.b.c|mod_mount", "<module>")
        chkDoc("", "a.b.c.d", "MODULE|a.b.c.d|sub_mount", "@mount(\"^.sub_mount\")\n<module>")
    }

    @Test fun testNamespace() {
        val code1 = "namespace a { namespace b { namespace c {} } }"
        chkDoc(code1, ":a", "NAMESPACE|:a", "<namespace> a")
        chkDoc(code1, ":a.b", "NAMESPACE|:a.b", "<namespace> b")
        chkDoc(code1, ":a.b.c", "NAMESPACE|:a.b.c", "<namespace> c")

        val code2 = "namespace a.b.c {}"
        chkDoc(code2, ":a", "NAMESPACE|:a", "<namespace> a")
        chkDoc(code2, ":a.b", "NAMESPACE|:a.b", "<namespace> b")
        chkDoc(code2, ":a.b.c", "NAMESPACE|:a.b.c", "<namespace> c")
    }

    @Test fun testNamespaceModifiers() {
        chkDoc("@mount('foo') namespace ns {}", ":ns", "NAMESPACE|:ns", "@mount(\"foo\")\n<namespace> ns")
        chkDoc("@external('foo') namespace ns {}", ":ns", "NAMESPACE|:ns", "@external(\"foo\")\n<namespace> ns")
        chkDoc("@deprecated namespace ns {}", ":ns", "NAMESPACE|:ns", "@deprecated\n<namespace> ns")
    }

    @Test fun testNamespaceModifiersDuplicate() {
        chkDoc("@mount('foo') namespace ns {} @mount('bar') namespace ns {}", ":ns", "NAMESPACE|:ns",
            "@mount(\"foo\")\n<namespace> ns")
        chkDoc("@deprecated namespace ns {} namespace ns {}", ":ns", "NAMESPACE|:ns", "@deprecated\n<namespace> ns")
        //chkDoc("namespace ns {} @deprecated namespace ns {}", ":ns", "NAMESPACE|:ns", "@deprecated\n<namespace> ns")
    }

    @Test fun testNamespaceMountBasic() {
        chkDoc("namespace ns {}", ":ns", "NAMESPACE|:ns", "<namespace> ns")
        chkDoc("@mount('ns') namespace ns {}", ":ns", "NAMESPACE|:ns", "@mount(\"ns\")\n<namespace> ns")
        chkDoc("@mount('') namespace ns {}", ":ns", "NAMESPACE|:ns", "@mount(\"\")\n<namespace> ns")
        chkDoc("@mount('sn') namespace ns {}", ":ns", "NAMESPACE|:ns", "@mount(\"sn\")\n<namespace> ns")

        chkDoc("@mount('foo') namespace ns {}", ":ns", "NAMESPACE|:ns", "@mount(\"foo\")\n<namespace> ns")
        chkDoc("@mount('.foo') namespace ns {}", ":ns", "NAMESPACE|:ns", "@mount(\".foo\")\n<namespace> ns")
        chkDoc("@mount('foo.') namespace ns {}", ":ns", "NAMESPACE|:ns", "@mount(\"foo.\")\n<namespace> ns")

        chkDoc("@mount('foo') namespace ns { namespace sub {} }", ":ns.sub", "NAMESPACE|:ns.sub", "<namespace> sub")
        chkDoc("@mount('foo.') namespace ns { namespace sub {} }", ":ns.sub", "NAMESPACE|:ns.sub", "<namespace> sub")
    }

    @Test fun testNamespaceMountComplex() {
        chkDoc("namespace ns1 { namespace ns2 { @mount('.foo') namespace ns3 {} } }", ":ns1.ns2.ns3",
            "NAMESPACE|:ns1.ns2.ns3",
            "@mount(\".foo\")\n<namespace> ns3",
        )
        chkDoc("namespace ns1 { namespace ns2 { @mount('foo') namespace ns3 {} } }", ":ns1.ns2.ns3",
            "NAMESPACE|:ns1.ns2.ns3",
            "@mount(\"foo\")\n<namespace> ns3",
        )
        chkDoc("namespace ns1 { namespace ns2 { @mount('foo.') namespace ns3 {} } }", ":ns1.ns2.ns3",
            "NAMESPACE|:ns1.ns2.ns3",
            "@mount(\"foo.\")\n<namespace> ns3",
        )
        chkDoc("namespace ns1 { namespace ns2 { @mount('.foo.') namespace ns3 {} } }", ":ns1.ns2.ns3",
            "NAMESPACE|:ns1.ns2.ns3",
            "@mount(\".foo.\")\n<namespace> ns3",
        )
        chkDoc("namespace ns1 { namespace ns2 { @mount('^.foo') namespace ns3 {} } }", ":ns1.ns2.ns3",
            "NAMESPACE|:ns1.ns2.ns3",
            "@mount(\"^.foo\")\n<namespace> ns3",
        )
        chkDoc("namespace ns1 { namespace ns2 { @mount('.ns3') namespace ns3 {} } }", ":ns1.ns2.ns3",
            "NAMESPACE|:ns1.ns2.ns3",
            "@mount(\".ns3\")\n<namespace> ns3",
        )
    }

    @Test fun testNamespaceQualifiedModifiers() {
        val def = "namespace a.b.c {}"

        chkDoc("@deprecated $def", ":a", "NAMESPACE|:a", "<namespace> a")
        chkDoc("@deprecated $def", ":a.b", "NAMESPACE|:a.b", "<namespace> b")
        chkDoc("@deprecated $def", ":a.b.c", "NAMESPACE|:a.b.c", "@deprecated\n<namespace> c")

        chkDoc("@external('foo') $def", ":a", "NAMESPACE|:a", "<namespace> a")
        chkDoc("@external('foo') $def", ":a.b", "NAMESPACE|:a.b", "<namespace> b")
        chkDoc("@external('foo') $def", ":a.b.c", "NAMESPACE|:a.b.c", "@external(\"foo\")\n<namespace> c")

        chkDoc("@mount('foo') $def", ":a", "NAMESPACE|:a", "<namespace> a")
        chkDoc("@mount('foo') $def", ":a.b", "NAMESPACE|:a.b", "<namespace> b")
        chkDoc("@mount('foo') $def", ":a.b.c", "NAMESPACE|:a.b.c", "@mount(\"foo\")\n<namespace> c")
    }

    @Test fun testEntity() {
        val code = "entity data { x: integer; mutable y: text; }"
        chkDoc(code, ":data", "ENTITY|:data|data", "<entity> data")
        chkDoc(code, ":data.x", "ENTITY_ATTR|:data.x", "x: [integer]")
        chkDoc(code, ":data.y", "ENTITY_ATTR|:data.y", "<mutable> y: [text]")
    }

    @Test fun testEntityModifiers() {
        chkDoc("@log entity data {}", ":data", "ENTITY|:data|data", "@log\n<entity> data")
        chkDoc("@mount('foo') entity data {}", ":data", "ENTITY|:data|foo", "@mount(\"foo\")\n<entity> data")
        chkDoc("@deprecated entity data {}", ":data", "ENTITY|:data|data", "@deprecated\n<entity> data")
        chkDoc("@log @external('foo') entity data {}", ":data", "ENTITY|:data|foo:data",
            "@log\n@external(\"foo\")\n<entity> data")
    }

    @Test fun testEntityMount() {
        val def = "entity data { x: integer; }"
        chkDoc(def, ":data", "ENTITY|:data|data", "<entity> data")
        chkDoc("@mount('foo') $def", ":data", "ENTITY|:data|foo", "@mount(\"foo\")\n<entity> data")
        chkDoc("@mount('foo') namespace { $def }", ":data", "ENTITY|:data|foo.data", "<entity> data")
        chkDoc("@mount('foo') $def", ":data.x", "ENTITY_ATTR|:data.x", "x: [integer]")
    }

    @Test fun testEntityExternal() {
        val code = "@external('foo') namespace ns { entity block; entity transaction; }"
        chkDoc(code, ":ns.block", "ENTITY|:ns.block|foo:blocks", "<entity> block")
        chkDoc(code, ":ns.transaction", "ENTITY|:ns.transaction|foo:transactions", "<entity> transaction")
    }

    @Test fun testEntityAttributes() {
        val x = "ENTITY_ATTR|:data.x"
        chkDoc("entity data { x: integer; }", ":data.x", x, "x: [integer]")
        chkDoc("entity data { mutable x: integer; }", ":data.x", x, "<mutable> x: [integer]")

        chkDoc("entity data { x: integer = 123; }", ":data.x", x, "x: [integer] = 123")
        chkDoc("entity data { mutable x: integer = 123; }", ":data.x", x, "<mutable> x: [integer] = 123")
        chkDoc("entity data { x: integer = min(1, 2); }", ":data.x", x, "x: [integer] = <...>")
        chkDoc("entity data { mutable x: integer = min(1, 2); }", ":data.x", x, "<mutable> x: [integer] = <...>")
    }

    @Test fun testEntityAttributesKeyIndex() {
        val x = "ENTITY_ATTR|:data.x"

        chkDoc("entity data { key x: integer; }", ":data.x", x, "<key> x: [integer]")
        chkDoc("entity data { index x: integer; }", ":data.x", x, "<index> x: [integer]")
        chkDoc("entity data { x: integer; key x; }", ":data.x", x, "<key> x: [integer]")
        chkDoc("entity data { x: integer; index x; }", ":data.x", x, "<index> x: [integer]")
        chkDoc("entity data { mutable x: integer; key x; }", ":data.x", x, "<mutable> <key> x: [integer]")
        chkDoc("entity data { mutable x: integer; index x; }", ":data.x", x, "<mutable> <index> x: [integer]")
        chkDoc("entity data { x: integer; key x; index x; }", ":data.x", x, "<key> x: [integer]\n\n<index> [x]")
        chkDoc("entity data { x: integer; index x; key x; }", ":data.x", x, "<key> x: [integer]\n\n<index> [x]")

        chkDoc("entity data { x: integer = 123; key x; }", ":data.x", x, "<key> x: [integer] = 123")
        chkDoc("entity data { x: integer = 123; index x; }", ":data.x", x, "<index> x: [integer] = 123")

        chkDoc("entity data { x: integer; y: text; z: boolean; index y, x; key z, x; index x, y, z; }", ":data.x", x,
            "x: [integer]\n\n<key> [z], [x]\n<index> [y], [x]\n<index> [x], [y], [z]")
        chkDoc("entity data { x: integer; y: text; z: boolean; key x; index y, x; key z, x; }", ":data.x", x,
            "<key> x: [integer]\n\n<key> [z], [x]\n<index> [y], [x]")
        chkDoc("entity data { x: integer; y: text; z: boolean; index x; index y, x; key z, x; }", ":data.x", x,
            "<index> x: [integer]\n\n<key> [z], [x]\n<index> [y], [x]")
    }

    @Test fun testObject() {
        val code = "object state { x: integer = 123; mutable y: text = 'Hello'; }"
        chkDoc(code, ":state", "OBJECT|:state|state", "<object> state")
        chkDoc(code, ":state.x", "OBJECT_ATTR|:state.x", "x: [integer] = 123")
        chkDoc(code, ":state.y", "OBJECT_ATTR|:state.y", "<mutable> y: [text] = \"Hello\"")
    }

    @Test fun testObjectModifiers() {
        chkDoc("@mount('foo') object data {}", ":data", "OBJECT|:data|foo", "@mount(\"foo\")\n<object> data")
        chkDoc("@deprecated object data {}", ":data", "OBJECT|:data|data", "@deprecated\n<object> data")
    }

    @Test fun testObjectMount() {
        val def = "object data { x: integer = 0; }"
        chkDoc(def, ":data", "OBJECT|:data|data", "<object> data")
        chkDoc("@mount('foo') $def", ":data", "OBJECT|:data|foo", "@mount(\"foo\")\n<object> data")
        chkDoc("@mount('foo') namespace { $def }", ":data", "OBJECT|:data|foo.data", "<object> data")
        chkDoc("@mount('foo') $def", ":data.x", "OBJECT_ATTR|:data.x", "x: [integer] = 0")
    }

    @Test fun testStruct() {
        val code = "struct data { x: integer = 123; mutable y: text = 'Hello'; }"
        chkDoc(code, ":data", "STRUCT|:data", "<struct> data")
        chkDoc(code, ":data.x", "STRUCT_ATTR|:data.x", "x: [integer] = 123")
        chkDoc(code, ":data.y", "STRUCT_ATTR|:data.y", "<mutable> y: [text] = \"Hello\"")
    }

    @Test fun testEnum() {
        val code = "enum colors { RED, GREEN, BLUE }"
        chkDoc(code, ":colors", "ENUM|:colors", "<enum> colors")
        chkDoc(code, ":colors.RED", "ENUM_VALUE|:colors.RED", "RED")
        chkDoc(code, ":colors.GREEN", "ENUM_VALUE|:colors.GREEN", "GREEN")
        chkDoc(code, ":colors.BLUE", "ENUM_VALUE|:colors.BLUE", "BLUE")
    }

    @Test fun testConstant() {
        chkDoc("val C = 123;", ":C", "CONSTANT|:C", "<val> C: [integer] = 123")
        chkDoc("val C = true;", ":C", "CONSTANT|:C", "<val> C: [boolean] = <true>")
        chkDoc("val C = 'Hello';", ":C", "CONSTANT|:C", "<val> C: [text] = \"Hello\"")
        chkDoc("val C: integer = 123;", ":C", "CONSTANT|:C", "<val> C: [integer] = 123")
        chkDoc("val C = 123 + 456;", ":C", "CONSTANT|:C", "<val> C: [integer] = 579")
        chkDoc("val C = min(123, 456);", ":C", "CONSTANT|:C", "<val> C: [integer]")
    }

    @Test fun testOperation() {
        val code = "operation op(x: integer, y: text = 'Hello') {}"
        chkDoc(code, ":op", "OPERATION|:op|op", "<operation> op(\n\tx: [integer],\n\ty: [text] = \"Hello\"\n)")
        chkDoc(code, ":op.x", "PARAMETER|x", "x: [integer]")
        chkDoc(code, ":op.y", "PARAMETER|y", "y: [text] = \"Hello\"")
    }

    @Test fun testOperationModifiers() {
        chkDoc("@mount('foo') operation op(){}", ":op", "OPERATION|:op|foo", "@mount(\"foo\")\n<operation> op()")
        chkDoc("@deprecated operation op(){}", ":op", "OPERATION|:op|op", "@deprecated\n<operation> op()")
    }

    @Test fun testOperationMount() {
        val def = "operation op() {}"
        chkDoc(def, ":op", "OPERATION|:op|op", "<operation> op()")
        chkDoc("@mount('foo') $def", ":op", "OPERATION|:op|foo", "@mount(\"foo\")\n<operation> op()")
        chkDoc("@mount('foo') namespace { $def }", ":op", "OPERATION|:op|foo.op", "<operation> op()")
    }

    @Test fun testQuery() {
        chkDoc("query q() = 123;", ":q", "QUERY|:q|q", "<query> q(): [integer]")
        chkDoc("query q() { return 123; }", ":q", "QUERY|:q|q", "<query> q(): [integer]")
        chkDoc("query q(): decimal = 123;", ":q", "QUERY|:q|q", "<query> q(): [decimal]")
        chkDoc("query q(x: integer, y: text) = 123;", ":q", "QUERY|:q|q",
            "<query> q(\n\tx: [integer],\n\ty: [text]\n): [integer]")
        chkDoc("query q(x: integer = 123) = 0;", ":q", "QUERY|:q|q", "<query> q(\n\tx: [integer] = 123\n): [integer]")
        chkDoc("query q(x: integer = min(1, 2)) = 0;", ":q", "QUERY|:q|q",
            "<query> q(\n\tx: [integer] = <...>\n): [integer]")
    }

    @Test fun testQueryModifiers() {
        chkDoc("@mount('foo') query q() = 0;", ":q", "QUERY|:q|foo", "@mount(\"foo\")\n<query> q(): [integer]")
        chkDoc("@deprecated query q() = 0;", ":q", "QUERY|:q|q", "@deprecated\n<query> q(): [integer]")
    }

    @Test fun testQueryMount() {
        val def = "query q() = 0;"
        chkDoc(def, ":q", "QUERY|:q|q", "<query> q(): [integer]")
        chkDoc("@mount('foo') $def", ":q", "QUERY|:q|foo", "@mount(\"foo\")\n<query> q(): [integer]")
        chkDoc("@mount('foo') namespace { $def }", ":q", "QUERY|:q|foo.q", "<query> q(): [integer]")
    }

    @Test fun testQueryParameters() {
        val code = "query q(x: integer, y: text = 'Hello', z: decimal = min(1, 2)) = 0;"
        chkDoc(code, ":q", "QUERY|:q|q",
            "<query> q(\n\tx: [integer],\n\ty: [text] = \"Hello\",\n\tz: [decimal] = <...>\n): [integer]")
        chkDoc(code, ":q.x", "PARAMETER|x", "x: [integer]")
        chkDoc(code, ":q.y", "PARAMETER|y", "y: [text] = \"Hello\"")
        chkDoc(code, ":q.z", "PARAMETER|z", "z: [decimal] = <...>")
    }

    @Test fun testFunction() {
        chkDoc("function f() {}", ":f", "FUNCTION|:f", "<function> f(): [unit]")
        chkDoc("function f() = 0;", ":f", "FUNCTION|:f", "<function> f(): [integer]")
        chkDoc("function f(): decimal = 0;", ":f", "FUNCTION|:f", "<function> f(): [decimal]")

        chkDoc("function f(x: integer) {}", ":f", "FUNCTION|:f", "<function> f(\n\tx: [integer]\n): [unit]")
        chkDoc("function f(x: integer) = '';", ":f", "FUNCTION|:f", "<function> f(\n\tx: [integer]\n): [text]")
        chkDoc("function f(x: integer, y: big_integer) = '';", ":f", "FUNCTION|:f",
            "<function> f(\n\tx: [integer],\n\ty: [big_integer]\n): [text]")
        chkDoc("function f(x: integer, y: big_integer, z: decimal) = '';", ":f", "FUNCTION|:f",
            "<function> f(\n\tx: [integer],\n\ty: [big_integer],\n\tz: [decimal]\n): [text]")
    }

    @Test fun testFunctionHeaderDefaultValues() {
        chkDoc("function f(x: integer = 123) {}", ":f", "FUNCTION|:f", "<function> f(\n\tx: [integer] = 123\n): [unit]")
        chkDoc("function f(x: decimal = 12.34) {}", ":f", "FUNCTION|:f", "<function> f(\n\tx: [decimal] = 12.34\n): [unit]")
        chkDoc("function f(x: text = 'bob') {}", ":f", "FUNCTION|:f", "<function> f(\n\tx: [text] = \"bob\"\n): [unit]")
        chkDoc("function f(x: integer = min(1, 2)) {}", ":f", "FUNCTION|:f", "<function> f(\n\tx: [integer] = <...>\n): [unit]")

        chkDoc("function f(x: integer = 123, y: text) {}", ":f", "FUNCTION|:f",
            "<function> f(\n\tx: [integer] = 123,\n\ty: [text]\n): [unit]")
        chkDoc("function f(x: integer, y: text = 'bob') {}", ":f", "FUNCTION|:f",
            "<function> f(\n\tx: [integer],\n\ty: [text] = \"bob\"\n): [unit]")
        chkDoc("function f(x: integer = 123, y: text = 'bob') {}", ":f", "FUNCTION|:f",
            "<function> f(\n\tx: [integer] = 123,\n\ty: [text] = \"bob\"\n): [unit]")
    }

    @Test fun testFunctionParameters() {
        chkDoc("function f(x: integer) {}", ":f.x", "PARAMETER|x", "x: [integer]")
        chkDoc("function f(x: integer = 123) {}", ":f.x", "PARAMETER|x", "x: [integer] = 123")
    }

    @Test fun testFunctionExtendable() {
        chkDoc("@extendable function f(x: integer);", ":f", "FUNCTION|:f",
            "@extendable\n<function> f(\n\tx: [integer]\n): [unit];")
        chkDoc("@extendable function f(x: integer) {}", ":f", "FUNCTION|:f",
            "@extendable\n<function> f(\n\tx: [integer]\n): [unit]\n{...}")
        chkDoc("@extendable function f(): boolean = false;", ":f", "FUNCTION|:f",
            "@extendable\n<function> f(): [boolean]\n{...}")
    }

    @Test fun testFunctionExtend() {
        file("module.rell", "@extendable function f(x: integer) {}")
        file("lib.rell", "module; namespace ns { @extendable function h(x: integer) {} }")
        chkDoc("@extend(f)\nfunction g(x: integer) {}", ":g", "FUNCTION|:g",
            "@extend([f])\n<function> g(\n\tx: [integer]\n): [unit]")
        chkDoc("import lib; @extend(lib.ns.h) function g(x: integer) {}", ":g", "FUNCTION|:g",
            "@extend([lib.ns.h])\n<function> g(\n\tx: [integer]\n): [unit]")
    }

    @Test fun testFunctionModifiers() {
        chkDoc("@deprecated function f() {}", ":f", "FUNCTION|:f", "@deprecated\n<function> f(): [unit]")
    }

    @Test fun testFunctionAbstract() {
        file("module.rell", "abstract module;")
        chkDoc("abstract function f(x: integer): text;", ":f", "FUNCTION|:f",
            "<abstract> <function> f(\n\tx: [integer]\n): [text];")
        chkDoc("abstract function f(x: integer): text = '';", ":f", "FUNCTION|:f",
            "<abstract> <function> f(\n\tx: [integer]\n): [text]\n{...}")
        chkDoc("abstract function f(): text;", ":f", "FUNCTION|:f", "<abstract> <function> f(): [text];")
        chkDoc("abstract function f(): text = '';", ":f", "FUNCTION|:f", "<abstract> <function> f(): [text]\n{...}")
    }

    @Test fun testImportModule() {
        file("lib.rell", "module;")
        file("sub.rell", "module; import root: ^;")
        chkDoc("import lib;", ":lib", "IMPORT|:lib", "<import> [lib]")
        chkDoc("import bil: lib;", ":bil", "IMPORT|:bil", "<import> bil: [lib]")
        chkDoc("import sub;", "sub:root", "IMPORT|sub:root", "<import> root: ['']")
    }

    @Test fun testImportExact() {
        file("lib.rell", "module; struct data {} namespace sub { struct sub_data {} }")

        chkDoc("import lib.{data};", ":data", "STRUCT|lib:data", "<struct> data")
        chkDoc("import lib.{tada:data};", ":tada", "IMPORT|:tada", "<import> [lib].{tada: [data]}")
        chkDoc("import lib.{ns:sub.*};", ":ns", "IMPORT|:ns", "<import> [lib].{ns: [sub].*}")
        chkDoc("import lib.{ns:sub.*};", ":ns.sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")
        chkDoc("import ns1: lib.{ns2:sub.*};", ":ns1", "IMPORT|:ns1", "<import> ns1: [lib].{...}")
        chkDoc("import ns1: lib.{ns2:sub.*};", ":ns1.ns2", "IMPORT|:ns1.ns2", "<import> ns1: [lib].{ns2: [sub].*}")
        chkDoc("import ns1: lib.{ns2:sub.*};", ":ns1.ns2.sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")

        chkDoc("import lib.{sub.sub_data};", ":sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")
        chkDoc("import lib.{tada:sub.sub_data};", ":tada", "IMPORT|:tada", "<import> [lib].{tada: [sub.sub_data]}")
        chkDoc("import ns: lib.{sub.sub_data};", ":ns", "IMPORT|:ns", "<import> ns: [lib].{...}")
        chkDoc("import ns: lib.{sub.sub_data};", ":ns.sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")
        chkDoc("import ns: lib.{tada:sub.sub_data};", ":ns", "IMPORT|:ns", "<import> ns: [lib].{...}")
        chkDoc("import ns: lib.{tada:sub.sub_data};", ":ns.tada",
            "IMPORT|:ns.tada", "<import> ns: [lib].{tada: [sub.sub_data]}")
    }

    @Test fun testImportExactIndirect() {
        file("lib.rell", "module; struct data {} namespace sub { struct sub_data {} }")

        file("mid.rell", """
            module;
            import lib;
            import mid_lib: lib;
            import lib.{data};
            import lib.{mid_data: data};
            import mid_wild: lib.*;
            import mid_ns1: lib.{data};
            import mid_ns2: lib.{tada:data};
            import mid_ns3: lib.{sub.*};
            import lib.{mid_ns4: sub.*};
            import mid_ns5: lib.{mid_ns6: sub.*};
        """)

        chkDoc("import mid.{lib};", ":lib", "IMPORT|mid:lib", "<import> [lib]")
        chkDoc("import mid.{mid_lib};", ":mid_lib", "IMPORT|mid:mid_lib", "<import> mid_lib: [lib]")
        chkDoc("import mid.{data};", ":data", "STRUCT|lib:data", "<struct> data")
        chkDoc("import mid.{mid_data};", ":mid_data", "STRUCT|lib:data", "<struct> data")
        chkDoc("import mid.{mid_wild};", ":mid_wild", "IMPORT|mid:mid_wild", "<import> mid_wild: [lib].*")

        chkDoc("import mid.{mid_ns1};", ":mid_ns1", "IMPORT|mid:mid_ns1", "<import> mid_ns1: [lib].{...}")
        chkDoc("import mid.{mid_ns2};", ":mid_ns2", "IMPORT|mid:mid_ns2", "<import> mid_ns2: [lib].{...}")
        chkDoc("import mid.{mid_ns2.tada};", ":tada", "STRUCT|lib:data", "<struct> data")
        chkDoc("import mid.{mid_ns3};", ":mid_ns3", "IMPORT|mid:mid_ns3", "<import> mid_ns3: [lib].{...}")
        chkDoc("import mid.{mid_ns3.sub_data};", ":sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")
        chkDoc("import mid.{mid_ns4};", ":mid_ns4", "IMPORT|mid:mid_ns4", "<import> [lib].{mid_ns4: [sub].*}")
        chkDoc("import mid.{mid_ns4.sub_data};", ":sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")

        chkDoc("import mid.{mid_ns5};", ":mid_ns5", "IMPORT|mid:mid_ns5", "<import> mid_ns5: [lib].{...}")
        chkDoc("import mid.{mid_ns5};", ":mid_ns5.mid_ns6", "IMPORT|mid:mid_ns5.mid_ns6", "<import> mid_ns5: [lib].{mid_ns6: [sub].*}")
        chkDoc("import mid.{mid_ns5};", ":mid_ns5.mid_ns6.sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")

        chkDoc("import mid.{mid_ns5.mid_ns6};", ":mid_ns6", "IMPORT|mid:mid_ns5.mid_ns6", "<import> mid_ns5: [lib].{mid_ns6: [sub].*}")
        chkDoc("import mid.{mid_ns5.mid_ns6};", ":mid_ns6.sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")
        chkDoc("import mid.{mid_ns5.mid_ns6.sub_data};", ":sub_data", "STRUCT|lib:sub.sub_data", "<struct> sub_data")
    }

    @Test fun testImportWildcard() {
        file("lib.rell", "module; struct data {}")
        chkDoc("import lib.*;", ":data", "STRUCT|lib:data", "<struct> data")
        chkDoc("import ns: lib.*;", ":ns", "IMPORT|:ns", "<import> ns: [lib].*")
        chkDoc("import ns: lib.*;", ":ns.data", "STRUCT|lib:data", "<struct> data")
    }

    @Test fun testImportWildcardIndirect() {
        file("lib.rell", "module; struct data {}")
        file("mid.rell", "module; import lib.*;")
        chkDoc("import mid.*;", ":data", "STRUCT|lib:data", "<struct> data")
        chkDoc("import ns: mid.*;", ":ns", "IMPORT|:ns", "<import> ns: [mid].*")
        chkDoc("import ns: mid.*;", ":ns.data", "STRUCT|lib:data", "<struct> data")
    }

    @Test fun testImportModifiers() {
        file("lib.rell", "@external module; @log entity data {}")
        chkDoc("@external('foo') import lib;", ":lib", "IMPORT|:lib", "@external(\"foo\")\n<import> [lib]")
    }

    @Test fun testModifierDeprecated() {
        chkDoc("@deprecated namespace ns {}", ":ns", "NAMESPACE|:ns", "@deprecated\n<namespace> ns")
        chkDoc("@deprecated entity data { name; }", ":data", "ENTITY|:data|data", "@deprecated\n<entity> data")
        chkDoc("@deprecated object state { x: integer = 0; }", ":state", "OBJECT|:state|state", "@deprecated\n<object> state")
        chkDoc("@deprecated operation op() {}", ":op", "OPERATION|:op|op", "@deprecated\n<operation> op()")
        chkDoc("@deprecated query q() = 0;", ":q", "QUERY|:q|q", "@deprecated\n<query> q(): [integer]")
        chkDoc("@deprecated struct data {}", ":data", "STRUCT|:data", "@deprecated\n<struct> data")
        chkDoc("@deprecated val MAGIC = 0;", ":MAGIC", "CONSTANT|:MAGIC", "@deprecated\n<val> MAGIC: [integer] = 0")
        chkDoc("@deprecated enum colors { RED }", ":colors", "ENUM|:colors", "@deprecated\n<enum> colors")
        chkDoc("@deprecated function f() = 0;", ":f", "FUNCTION|:f", "@deprecated\n<function> f(): [integer]")
    }

    @Test fun testMountNameModule() {
        file("lib.rell", """
            @mount('foo') module;
            entity data {}
            object state {}
            operation op(){}
            query q() = 0;
        """)
        chkDoc("", "lib:data", "ENTITY|lib:data|foo.data", "<entity> data")
        chkDoc("", "lib:state", "OBJECT|lib:state|foo.state", "<object> state")
        chkDoc("", "lib:op", "OPERATION|lib:op|foo.op", "<operation> op()")
        chkDoc("", "lib:q", "QUERY|lib:q|foo.q", "<query> q(): [integer]")
    }

    @Test fun testMountNameNamespace() {
        val defs = """
            entity data {}
            object state {}
            operation op(){}
            query q() = 0;
        """
        chkMountNameNamespace("namespace ns { $defs }", "ns.", "ns")
        chkMountNameNamespace("@mount('foo') namespace ns { $defs }", "ns.", "foo")
        chkMountNameNamespace("@mount('foo') namespace { $defs }", "", "foo")
    }

    private fun chkMountNameNamespace(code: String, namePrefix: String, mountPrefix: String) {
        chkDoc(code, ":${namePrefix}data", "ENTITY|:${namePrefix}data|$mountPrefix.data", "<entity> data")
        chkDoc(code, ":${namePrefix}state", "OBJECT|:${namePrefix}state|$mountPrefix.state", "<object> state")
        chkDoc(code, ":${namePrefix}op", "OPERATION|:${namePrefix}op|$mountPrefix.op", "<operation> op()")
        chkDoc(code, ":${namePrefix}q", "QUERY|:${namePrefix}q|$mountPrefix.q", "<query> q(): [integer]")
    }

    private fun chkDoc(code: String, name: String, expectedHeader: String, expectedCode: String) {
        val def = getDocDef(code, name)
        BaseLTest.chkDoc(def.docSymbol, expectedHeader, expectedCode)
    }
}
