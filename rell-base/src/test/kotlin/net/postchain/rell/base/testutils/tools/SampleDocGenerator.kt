/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

// Sample docs HTML generator. Purpose: to see which docs format is better.

package net.postchain.rell.base.testutils.tools

import com.google.common.io.Files
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.ide.BaseIdeSymbolTest
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.dsl.BaseLTest
import net.postchain.rell.base.testutils.LibModuleTester
import net.postchain.rell.base.utils.doc.DocCodeTokenVisitor
import net.postchain.rell.base.utils.doc.DocSymbol
import java.io.File

fun main() {
    val buf = StringBuilder()
    generateDocsFile(buf)

    val s = buf.toString()
    val file = File("target/docs.html")
    file.parentFile.mkdirs()
    Files.asCharSink(file, Charsets.UTF_8).write(s)
}

private fun generateDocsFile(buf: StringBuilder) {
    buf.append("<!DOCTYPE html>\n")
    buf.append("<html>\n")
    buf.append("<head><title>Docs Demo</title></head>\n")
    buf.append("<body>\n\n")

    generateDocSamples(buf)

    buf.append("</body>\n")
    buf.append("</html>\n")
}

private fun generateDocSamples(buf: StringBuilder) {
    docExpr(buf, "Namespace", "crypto.sha256(x'')", "crypto")
    docExpr(buf, "Namespace constant", "rell.test.privkeys.bob", "bob")
    docExpr(buf, "Namespace property", "chain_context.blockchain_rid", "blockchain_rid")
    docExpr(buf, "Namespace property (special)", "sample.special_prop", "special_prop")
    docExpr(buf, "Namespace function (simple)", "max(1, 2)", "max")
    docExpr(buf, "Namespace function (generic)", "try_call(integer.from_hex('', *), -1)", "try_call")
    docExpr(buf, "Namespace function (deprecated)", "sample.dep_fun_warn()", "dep_fun_warn")
    docExpr(buf, "Namespace function (deprecated)", "sample.dep_fun_err()", "dep_fun_err", err = true)
    docExpr(buf, "Namespace function (alias)", "sample.fun_alias()", "fun_alias")
    docExpr(buf, "Namespace function (alias)", "sample.fun_alias_warn()", "fun_alias_warn")
    docExpr(buf, "Namespace function (alias)", "sample.fun_alias_err()", "fun_alias_err", err = true)
    docExpr(buf, "Namespace function (special)", "exists([123])", "exists")
    docType(buf, "Namespace struct", "gtx_operation", "gtx_operation")
    docMember(buf, "Struct attribute", "gtx_operation", "args", "args")
    docExpr(buf, "Namespace link (function)", "sample.link_fun()", "link_fun")
    docExpr(buf, "Namespace link (function)", "sample.link_fun_warn()", "link_fun_warn")

    docType(buf, "Type (simple)", "byte_array", "byte_array")
    docType(buf, "Type (alias)", "timestamp", "timestamp")
    docExpr(buf, "Type constructor", "integer('', 10)", "integer")
    docExpr(buf, "Type constructor (generic)", "sample.sample_type([''], ['':0])", "sample_type")
    docExpr(buf, "Type constant", "integer.MAX_VALUE", "MAX_VALUE")
    docMember(buf, "Type property", "sample.sample_type", "prop", "prop")
    docMember(buf, "Type property (special)", "sample.sample_type", "spec_prop", "spec_prop")
    docExpr(buf, "Type function", "''.size()", "size")
    docExpr(buf, "Type function (static)", "integer.from_hex('')", "from_hex")
    docMember(buf, "Type function (special)", "sample.sample_type", "spec_fun()", "spec_fun")
    docMember(buf, "Type function (deprecated)", "sample.sample_type", "dep_fun_warn()", "dep_fun_warn")
    docMember(buf, "Type function (deprecated)", "sample.sample_type", "dep_fun_err()", "dep_fun_err", err = true)
    docMember(buf, "Type function (alias)", "sample.sample_type", "fun_alias()", "fun_alias")
    docMember(buf, "Type function (alias)", "sample.sample_type", "fun_alias_warn()", "fun_alias_warn")
    docMember(buf, "Type function (alias)", "sample.sample_type", "fun_alias_err()", "fun_alias_err", err = true)

    docType(buf, "Generic type", "list<integer>", "list")
    docType(buf, "Generic type", "map<integer, text>", "map")
    docExpr(buf, "Generic type constructor: list", "list<integer>()", "list")
    docExpr(buf, "Generic type constructor: list", "list<integer>([0])", "list")
    docExpr(buf, "Generic type constructor: list", "list([0])", "list")
    docExpr(buf, "Generic type constructor: map", "map<integer, text>()", "map")
    docExpr(buf, "Generic type constructor: map", "map<integer, text>([0:''])", "map")
    docExpr(buf, "Generic type constructor: map", "map([0:''])", "map")
    docExpr(buf, "Generic type constructor (generic)", "sample.sample_map<integer, text>([0.0], [0.0:(0,'')])", "sample_map")
    docExpr(buf, "Generic type constructor (generic)", "sample.sample_map([0.0], [0.0:(0,'')])", "sample_map")
    docExpr(buf, "Generic type function: list", "[0].get(0)", "get")
    docExpr(buf, "Generic type function: map", "[0:''].get(0)", "get")
    docExpr(buf, "Generic type function: map (generic)", "[0:''].get_or_default(0, null)", "get_or_default")

    docExpr(buf, "Type extension function: text (value)", "''.to_gtv()", "to_gtv")
    docExpr(buf, "Type extension function: text (static)", "text.from_gtv(gtv.from_json(''))", "from_gtv")
    docExpr(buf, "Type extension function: set (value)", "set<integer>().to_gtv()", "to_gtv")
    docExpr(buf, "Type extension function: set (static)", "set<integer>.from_gtv(gtv.from_json(''))", "from_gtv")

    docDef(buf, "User function", "function f(x: integer, y: text) {}", "f")
    docDef(buf, "Parameter type (function)", "function f(x: ((integer,text)->boolean)?) {}", "x")
    docDef(buf, "Parameter type (tuple)", "function f(y: (a:integer,b:text)) {}", "y")
    docDef(buf, "Parameter type (map)", "function f(z: map<integer,list<(text,boolean)>>) {}", "z")
}

private fun docType(buf: StringBuilder, title: String, type: String, name: String, err: Boolean = false) {
    generateDoc(buf, title, "struct _s { x: $type; }", name, err = err)
}

private fun docExpr(buf: StringBuilder, title: String, code: String, name: String, err: Boolean = false) {
    generateDoc(buf, title, "function _f() = $code;", name, err = err)
}

private fun docMember(buf: StringBuilder, title: String, type: String, member: String, name: String, err: Boolean = false) {
    generateDoc(buf, title, "function _f(x: $type) = x.$member;", name, err = err)
}

private fun docDef(buf: StringBuilder, title: String, code: String, name: String) {
    generateDoc(buf, title, code, name)
}

private fun generateDoc(buf: StringBuilder, title: String, code: String, name: String, err: Boolean = false) {
    val syms = BaseIdeSymbolTest.getDocSymbols(code, SampleDocLib.MODULE, err = err)
    val doc = syms.getValue(name)

    buf.append("<hr/>")
    buf.append("<p>")
    buf.append(escapeHtml(title))
    buf.append("</p>")
    buf.append("<pre>")
    buf.append(escapeHtml(code))
    buf.append("</pre>\n")

    generateDocSymbol(buf, doc)
}

private fun generateDocSymbol(buf: StringBuilder, doc: DocSymbol) {
    buf.append("""<table style="border: solid 1px; border-collapse: collapse;" border="1">""")
    buf.append("\n")

    val tdStyle = """style="padding:5px;""""

    buf.append("<tr><td $tdStyle>")
    buf.append("""<small>${doc.kind.msg}</small><br/>""")
    buf.append("<tt style=\"font-size: 1.25em\">")
    buf.append(escapeHtml(doc.symbolName.strCode()))
    buf.append("</tt>")
    buf.append("</td></tr>\n")

    buf.append("<tr><td $tdStyle>")
    buf.append("<pre style=\"font-size: 1.25em; padding: 0; margin: 0;\">")
    doc.declaration.code.visit(StringBuilderDocCodeTokenVisitor(buf))
    buf.append("</pre>")
    buf.append("</td></tr>\n")

    buf.append("<tr><td $tdStyle>")
    buf.append("Comment header.<br/>Comment line 1.<br/>Comment line 2.")
    buf.append("</td></tr>\n")

    buf.append("</table>\n\n")
}

private fun escapeHtml(s: String): String {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

private class StringBuilderDocCodeTokenVisitor(private val buf: StringBuilder): DocCodeTokenVisitor {
    override fun tab() {
        buf.append("    ")
    }

    override fun raw(s: String) {
        buf.append(escapeHtml(s))
    }

    override fun keyword(s: String) {
        buf.append("<span style=\"font-weight: bold;\">")
        buf.append(s)
        buf.append("</span>")
    }

    override fun link(s: String) {
        buf.append("<a href=\"about:blank\">")
        buf.append(escapeHtml(s))
        buf.append("</a>")
    }
}

private object SampleDocLib {
    val MODULE: C_LibModule = C_LibModule.make("sample", Lib_Rell.MODULE) {
        namespace("sample") {
            property("special_prop", BaseLTest.makeNsProp(Rt_UnitValue))

            function("dep_fun_warn", result = "integer") {
                deprecated("other_fun", error = false)
                body { -> Rt_UnitValue }
            }

            function("dep_fun_err", result = "integer") {
                deprecated("other_fun", error = true)
                body { -> Rt_UnitValue }
            }

            function("fun_real", result = "integer") {
                alias("fun_alias")
                alias("fun_alias_warn", deprecated = C_MessageType.WARNING)
                alias("fun_alias_err", deprecated = C_MessageType.ERROR)
                body { -> Rt_UnitValue }
            }

            alias("link_fun", "fun_real")
            alias("link_fun_warn", "dep_fun_warn")

            type("sample_type") {
                LibModuleTester.setRTypeFactory(this, ::getModule, "sample.sample_type")

                constructor {
                    generic("U", subOf = "immutable")
                    param("a", type = "list<U>")
                    param("b", type = "map<U, integer>")
                    body { -> Rt_UnitValue }
                }

                property("prop", type = "integer") { value { _ -> Rt_UnitValue } }
                property("pure_prop", pure = true, type = "integer") { value { _ -> Rt_UnitValue } }
                property("spec_prop", type = "integer", C_SysFunctionBody.simple { _ -> Rt_UnitValue })

                function("spec_fun", BaseLTest.makeTypeFun())

                function("dep_fun_warn", result = "integer") {
                    deprecated("other_fun", error = false)
                    body { -> Rt_UnitValue }
                }

                function("dep_fun_err", result = "integer") {
                    deprecated("other_fun", error = true)
                    body { -> Rt_UnitValue }
                }

                function("fun_real", result = "integer") {
                    alias("fun_alias")
                    alias("fun_alias_warn", deprecated = C_MessageType.WARNING)
                    alias("fun_alias_err", deprecated = C_MessageType.ERROR)
                    body { -> Rt_UnitValue }
                }
            }

            type("sample_map") {
                generic("K", subOf = "immutable")
                generic("V")
                parent("iterable<(K, V)>")

                LibModuleTester.setRTypeFactory(this, ::getModule, "sample.sample_map", genericCount = 2)

                constructor {
                    generic("U", subOf = "immutable")
                    param("a", type = "list<U>")
                    param("b", type = "map<U, (K, V)>")
                    body { -> Rt_UnitValue }
                }
            }
        }
    }

    private fun getModule(): C_LibModule = MODULE
}
