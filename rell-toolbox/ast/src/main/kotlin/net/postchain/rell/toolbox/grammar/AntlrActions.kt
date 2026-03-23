/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

@file:Suppress("all")
package net.postchain.rell.lsp.grammar

import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.grammar.GrammarUtils
import java.util.*

// TODO: This action generator should be revisited as current implementation was used for initial generation  and then modified manually.
fun main() {
    AntlrGenUtils.printHeader()

    println("package net.postchain.rell.lsp.compiler;\n")
    println("import org.antlr.v4.runtime.ParserRuleContext;\n")
    println("import static net.postchain.rell.lsp.parser.RellParser.*;\n")

    println("public final class AntlrToRell {")

    val actions = generateAntlrActions()

    val transforms = actions.filterValues { it.transform != null }.keys
    for (type in transforms) {
        val name = typeToTransform(type)
        println("    private static final RellcTransformer $name = RellcUtils.transformer(\"$type\");")
    }
    if (transforms.isNotEmpty()) println()

    println("    public static Object process(AntlrToRellContext ctx, ParserRuleContext node) {")
    println("        if (node == null) return null;\n")

    println("        switch (node.getRuleIndex()) {")

    for ((type, action) in actions) {
        if( type.lowercase(Locale.getDefault()).contains("module")) {
            println("Type ERROR: $type")
        }
        val id = typeToId(type)
        println("            case $id: {")

        val attrs = action.action.generate(type)
        var varStr = ""
        attrs.forEachIndexed { index, _ ->
            varStr += "var_$index"
            if ( index != attrs.size - 1)  { varStr += ", " }
        }

        val tupleExpr = "RellcUtils.tuple($varStr)"

        val expr = if (action.transform == null) tupleExpr else {
            println("                Object tup = $tupleExpr;")
            val transformName = typeToTransform(type)
            "$transformName.transform(ctx, node, tup)"
        }

        println("                return $expr;")
        println("            }")
    }

    println("            default:")
    println("                throw new IllegalArgumentException(node.getText() + \" \" + node.getRuleIndex());")
    println("        }")
    println("    }")
    println("}")
}


private fun typeToId(type: String): String {
    return "RULE_rule$type"
}

private fun typeToTransform(type: String): String {
    return "TRANS_" + camelCaseToUpper(type)
}

// Must use same algorithm as the Antlr code generator.
private fun camelCaseToUpper(s: String): String {
    val b = StringBuilder(s.length * 2)
    for (i in s.indices) {
        val c = s[i]
        if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(s[i - 1])) b.append('_')
        if (c == '_' && i > 0 && Character.isUpperCase(s[i - 1])) continue
        b.append(Character.toUpperCase(c))
    }
    return b.toString()
}

class AntlrActionEx(val action: AntlrAction, val transform: ((Any) -> Any)?)

sealed class AntlrAction {
    abstract fun generate(type: String): List<String>
}

class AntlrAction_Token(private val name: String?) : AntlrAction() {

    override fun generate(type: String): List<String> {
        val tail = name?.lowercase(Locale.getDefault())?.capitalize() ?: ""
        println("                Object var_0 = RellcUtils.token$tail(node);")
        return listOf("a")
    }
}

class AntlrAttr(val name: String, val many: Boolean)

class AntlrAction_General(private val attrs: List<AntlrAttr>) : AntlrAction() {

    override fun generate(type: String): List<String> {
        for (i in attrs.indices) {
            val attr = attrs[i]
            val expr = if (attr.many) {
                "RellcUtils.processList(ctx, node.getRuleContexts(${attr.name}.class))"
            } else {
                "RellcUtils.processObject(ctx, node.getRuleContext(${attr.name}.class, 0))"
            }
            println("                Object var_$i = $expr;")
        }

        return attrs.map { it.name }
    }
}

object AntlrGenUtils {
    fun printHeader() {
        val timestamp = System.currentTimeMillis()
        val timestampStr = GrammarUtils.timestampToString(timestamp)
        println("// Rell version: ${RellVersions.VERSION_STR}")
        println("// Timestamp: $timestamp ($timestampStr)")
    }
}
