/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.grammar

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.capitalizeEx
import net.postchain.rell.base.utils.mapToImmList

fun main() {
    XtextGenUtils.printHeader()

    println("package net.postchain.rellide.lang.compiler;\n")
    println("import org.eclipse.emf.ecore.EObject;\n")
    println("import net.postchain.rellide.xtext.rell.RellPackage;\n")

    println("public final class XtextToRell {")

    val actions = generateXtextActions()

    val transforms = actions.filterValues { it.transform != null }.keys
    for (type in transforms) {
        val name = typeToTransform(type)
        println("    private static final RellcTransformer $name = RellcUtils.transformer(\"$type\");")
    }
    if (!transforms.isEmpty()) println()

    println("    public static Object process(XtextToRellContext ctx, EObject obj) {")
    println("        if (obj == null) return null;\n")

    println("        switch (obj.eClass().getClassifierID()) {")

    for ((type, action) in actions) {
        val id = typeToId(type)
        println("            case $id: {")

        val attrs = action.action.generate(type)
        val attrsStr = attrs.joinToString(", ")

        val tupleExpr = "RellcUtils.tuple($attrsStr)"

        val expr = if (action.transform == null) tupleExpr else {
            println("                Object tup = $tupleExpr;")
            val transformName = typeToTransform(type)
            "$transformName.transform(ctx, obj, tup)"
        }

        println("                return $expr;")
        println("            }")
    }

    println("            default:")
    println("                throw new IllegalArgumentException(obj.eClass().getName());")
    println("        }")
    println("    }")
    println("}")
}

private fun typeToId(type: String): String {
    return "RellPackage." + camelCaseToUpper(type)
}

private fun typeToTransform(type: String): String {
    return "TRANS_" + camelCaseToUpper(type)
}

// Must use same algorithm as the Xtext code generator.
private fun camelCaseToUpper(s: String): String = buildString(s.length * 2) {
    for (i in s.indices) {
        val c = s[i]
        if (c.isUpperCase() && i > 0 && s[i - 1].isLowerCase()) append('_')
        if (c == '_' && i > 0 && s[i - 1].isUpperCase()) continue
        append(c.uppercaseChar())
    }
}

class XtextActionEx(val action: XtextAction, val transform: ((Any) -> Any)?)

sealed class XtextAction {
    abstract fun generate(type: String): List<String>
}

class XtextAction_Token(private val name: String?): XtextAction() {
    override fun generate(type: String): List<String> {
        val tail = name?.capitalizeEx().orEmpty()
        println("                Object a = RellcUtils.token$tail(obj);")
        return listOf("a")
    }
}

class XtextAttr(val name: String, val many: Boolean)

class XtextAction_General(private val attrs: ImmList<XtextAttr>): XtextAction() {
    override fun generate(type: String): ImmList<String> {
        val fullType = "net.postchain.rellide.xtext.rell.$type"
        println("                $fullType node = ($fullType) obj;")

        for (attr in attrs) {
            val getter = "get" + attr.name.uppercase()
            val expr = if (attr.many) {
                "RellcUtils.processList(ctx, node.$getter())"
            } else {
                "RellcUtils.processObject(ctx, node.$getter())"
            }
            println("                Object ${attr.name} = $expr;")
        }

        return attrs.mapToImmList { it.name }
    }
}

object XtextGenUtils {
    fun printHeader() {
        val timestamp = System.currentTimeMillis()
        val timestampStr = GrammarUtils.timestampToString(timestamp)
        println("// Rell version: ${RellVersions.VERSION_STR}")
        println("// Timestamp: $timestamp ($timestampStr)")
    }
}
