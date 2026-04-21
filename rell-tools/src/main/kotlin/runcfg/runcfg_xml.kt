/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.runcfg

import net.postchain.rell.api.base.RellCliBasicException
import net.postchain.rell.base.utils.GeneralDir
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.checkNull
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max

class RellXmlParser(private val preserveWhitespace: Boolean = false) {
    fun parse(path: String, text: String, treePath: List<String> = listOf()): RellXmlElement {
        val doc = parseDocument(path, text)
        val res = convertDocument(path, doc, treePath)
        return res
    }

    private fun parseDocument(path: String, text: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val reader = StringReader(text)
        val source = InputSource(reader)
        source.systemId = path
        val doc = builder.parse(source)
        return doc
    }

    private fun convertDocument(file: String, doc: Document, treePath: List<String>): RellXmlElement {
        val docNode = makeDomNode(doc)

        check(docNode.attrs.isEmpty()) { "Document has attributes: ${docNode.attrs}" }
        checkEquals(docNode.elems.size, 1) { "Document has ${docNode.elems.size} elements" }
        checkNull(docNode.text) { "Document has text" }

        val res = convertElement(file, treePath, docNode.elems[0])
        return res
    }

    private fun convertElement(file: String, parentTreePath: List<String>, elem: Element): RellXmlElement {
        val node = makeDomNode(elem)

        val treePath = parentTreePath + elem.tagName

        val elems = mutableListOf<RellXmlElement>()
        for (subNodeElem in node.elems) {
            val subElem = convertElement(file, treePath, subNodeElem)
            elems.add(subElem)
        }

        return RellXmlElement(file, treePath, elem.tagName, node.attrs, elems, node.text)
    }

    private fun makeDomNode(node: Node): DomNode {
        val attrs = mutableMapOf<String, String>()

        if (node.attributes != null) {
            for (i in 0 until node.attributes.length) {
                val attrNode = node.attributes.item(i)
                val name = attrNode.nodeName
                val value = attrNode.nodeValue
                check(name !in attrs) { "Duplicate attribute name: $name" }
                attrs[name] = value
            }
        }

        val elems = mutableListOf<Element>()
        val textBuf = StringBuilder()

        val subNodes = childNodes(node)
        for (subNode in subNodes) {
            when (subNode.nodeType) {
                Node.ELEMENT_NODE -> elems.add(subNode as Element)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> textBuf.append(subNode.nodeValue)
            }
        }

        var textRaw = textBuf.toString()
        if (elems.isNotEmpty() && textRaw.isBlank()) textRaw = ""
        if (!preserveWhitespace) textRaw = textRaw.trim()
        val text = textRaw.ifEmpty { null }

        return DomNode(attrs, elems, text)
    }

    private fun childNodes(node: Node): List<Node> {
        val res = mutableListOf<Node>()
        val childNodes = node.childNodes
        for (i in 0 until childNodes.length) {
            res.add(childNodes.item(i))
        }
        return res
    }
}

object RellXmlIncluder {
    fun includeFiles(xml: RellXmlElement, includeDir: GeneralDir): RellXmlElement {
        val list = processElement(xml, true, includeDir)
        checkEquals(list.size, 1)
        return list[0]
    }

    private fun processElement(xml: RellXmlElement, root: Boolean, includeDir: GeneralDir): List<RellXmlElement> =
        if (xml.tag == "include") {
            processInclude(xml, root, includeDir)
        } else {
            processOther(xml, includeDir)
        }

    private fun processInclude(xml: RellXmlElement, root: Boolean, includeDir: GeneralDir): List<RellXmlElement> {
        xml.checkNoText()
        xml.checkNoElems()

        val attrs = xml.attrs()
        val src = attrs.getNoBlank("src")
        val includeRoot = attrs.getBooleanOpt("root") ?: true
        attrs.checkNoMore()

        val text = includeDir.readTextOpt(src)
        xml.check(text != null) { "file not found: '$src'" }
        text!!

        val path = includeDir.absolutePath(src)
        val xml2 = RellXmlParser().parse(path, text)

        val xml3 = includeFiles(xml2, includeDir)
        val res = if (includeRoot) listOf(xml3) else xml3.elems
        xml.check(!root || res.size == 1) { "expected exactly one nested element, but found ${res.size}" }

        return res
    }

    private fun processOther(xml: RellXmlElement, includeDir: GeneralDir): List<RellXmlElement> {
        val elems = mutableListOf<RellXmlElement>()
        var diff = false

        for (elem in xml.elems) {
            val subElems = processElement(elem, false, includeDir)
            diff = diff || (subElems.size != 1 || subElems[0] !== elem)
            elems.addAll(subElems)
        }

        if (!diff) {
            return listOf(xml)
        }

        val res = RellXmlElement(xml.file, xml.treePath, xml.tag, xml.attrs, elems, xml.text)
        return listOf(res)
    }
}

class RellXmlElement(
    val file: String,
    val treePath: List<String>,
    val tag: String,
    val attrs: Map<String, String>,
    val elems: List<RellXmlElement>,
    val text: String?,
) {
    fun parentTreePath() = treePath.subList(0, max(0, treePath.size - 1))
    fun attrs() = RellXmlAttrsParser(this)

    fun <T> parseText(parser: (String) -> T): T {
        val s = text ?: ""
        val res = try {
            parser(s)
        } catch (_: Throwable) {
            throw error("invalid value: '$s'")
        }
        return res
    }

    fun checkTag(expectedTag: String) {
        if (tag != expectedTag) {
            throw error("expected element '$expectedTag'")
        }
    }

    fun checkNoText() {
        check(text == null) { "must have no text" }
    }

    fun checkNoElems() {
        check(elems.isEmpty()) { "must have no nested elements" }
    }

    inline fun check(b: Boolean, msgCode: () -> String) {
        if (!b) {
            val msg = msgCode()
            throw error(msg)
        }
    }

    inline fun <T> checkNotNull(v: T?, msgCode: () -> String): T {
        this.check(v != null, msgCode)
        return v!!
    }

    fun errorTag(): RuntimeException = error("this element is not expected here")

    fun error(msg: String): RuntimeException {
        val parentPath = parentTreePath()
        val path = if (parentPath.isEmpty()) "document root" else "path: " + parentPath.joinToString(" -> ")
        val fullMsg = "$file: element '$tag': $msg [$path]"
        throw RellCliBasicException(fullMsg)
    }
}

class RellXmlAttrsParser(private val elem: RellXmlElement) {
    private val touchedKeys = mutableSetOf<String>()

    fun getOpt(key: String): String? {
        val res = elem.attrs[key]
        if (res != null) {
            touchedKeys.add(key)
        }
        return res
    }

    fun get(key: String): String {
        val res = getOpt(key) ?: throw elem.error("missing required attribute '$key'")
        return res
    }

    fun getNoBlank(key: String): String {
        return getEx(key) { if (it.isEmpty()) "empty string" else null }
    }

    fun getNoBlankOpt(key: String): String? {
        return getOptEx(key) { if (it.isEmpty()) "empty string" else null }
    }

    fun get(key: String, checker: (String) -> Boolean): String {
        return getEx(key) { if (checker(it)) null else "" }
    }

    fun getEx(key: String, checker: (String) -> String?): String {
        return getType(key, null, checker) { it }
    }

    fun getOptEx(key: String, checker: (String) -> String?): String? {
        return getTypeOpt(key, null, checker) { it }
    }

    fun getLongOpt(key: String, checker: (Long) -> Boolean = { true }): Long? {
        return getTypeOpt(key, "Long", { if (checker(it)) null else "" }) { it.toLong() }
    }

    fun getLong(key: String, checker: (Long) -> Boolean = { true }): Long {
        return getType(key, "Long", { if (checker(it)) null else "" }) { it.toLong() }
    }

    fun getBooleanOpt(key: String): Boolean? {
        return getTypeOpt(key, "Boolean") {
            when (it) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException(it)
            }
        }
    }

    fun checkNoMore() {
        val moreAttrs = elem.attrs.keys - touchedKeys
        if (!moreAttrs.isEmpty()) {
            val attrs = moreAttrs.sorted().joinToString(", ") { "'$it'" }
            throw elem.error("unexpected attribute${if (moreAttrs.size == 1) "" else "s"}: $attrs")
        }
    }

    fun <T> getTypeOpt(key: String, type: String?, checker: (T) -> String? = { null }, parser: (String) -> T): T? {
        val s = getOpt(key) ?: return null
        return parseType(key, type, s, parser, checker)
    }

    fun <T> getType(key: String, type: String?, checker: (T) -> String? = { null }, parser: (String) -> T): T {
        val s = get(key)
        return parseType(key, type, s, parser, checker)
    }

    private fun <T> parseType(
        key: String,
        type: String?,
        value: String,
        parser: (String) -> T,
        checker: (T) -> String?
    ): T {
        val res = try {
            parser(value)
        } catch (_: Throwable) {
            val msg = parseErrMsg(key, type, value) + " (parsing failed)"
            throw elem.error(msg)
        }

        val err = checker(res)
        if (err != null) {
            var msg = parseErrMsg(key, type, value)
            if (!err.isEmpty()) msg += " ($err)"
            throw elem.error(msg)
        }

        return res
    }

    private fun parseErrMsg(key: String, type: String?, value: String): String {
        var msg = "attribute '$key' has invalid value"
        if (type != null) msg += " of type $type"
        msg += ": '$value'"
        return msg
    }
}

private class DomNode(val attrs: Map<String, String>, val elems: List<Element>, val text: String?)
