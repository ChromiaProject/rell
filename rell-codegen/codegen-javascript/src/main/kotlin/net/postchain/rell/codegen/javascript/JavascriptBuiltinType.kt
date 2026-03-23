/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.javascript

import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.util.BuiltinType
import java.util.Locale


enum class JavascriptBuiltinType(val builtin: TypeAssertion) : BuiltinType {
    BooleanAssertion(BooleanAssertionJs),
    NumberAssertion(NumberAssertionJs),
    BigIntegerAssertion(BigIntegerAssertionJs),
    StringAssertion(StringAssertionJs),
    ObjectAssertion(ObjectAssertionJs),
    BufferAssertion(BufferAssertionJs),
    SetAssertion(SetAssertionJs),
    ArrayAssertion(ArrayAssertionJs),
    AnyAssertion(AnyAssertionJs)
    ;

    override fun createBuiltin() = builtin
}

object BooleanAssertionJs : TypeAssertion("assertBoolean", "boolean")

object NumberAssertionJs : TypeAssertion("assertNumber", "number")
object BigIntegerAssertionJs : TypeAssertion("assertBigInteger", "bigint")

object StringAssertionJs : TypeAssertion("assertString", "string")

object ObjectAssertionJs : TypeAssertion("assertObject", "object")

object BufferAssertionJs : TypeAssertion("assertBuffer", "Buffer", true)

object SetAssertionJs : TypeAssertion("assertSet", "Set", true)

object ArrayAssertionJs : TypeAssertion("assertArray", "Array", true)

object AnyAssertionJs : TypeAssertion("assertAny", "")

abstract class TypeAssertion(val functionName: String, private val jsType: String, private val complex: Boolean = false) : Builtin {

    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")
    override val docSymbol = DocSymbol.NONE

    val className = CamelCaseClassName("", functionName, functionName.uppercase(Locale.getDefault()), "")

    override fun format(): String {
        return if (jsType.isNotBlank()) {
            """
            |export function $functionName(arg) {
            |${"\t"}if(${assertionString()}) throw new Error("Expected input to be $jsType")
            |}
            """.trimMargin()
        } else {
            """
            |/* Unsupported Rell type for JS assertion, defaults to true */
            |export function $functionName(arg) {
            |${"\t"}return true
            |}
            """.trimMargin()
        }
    }

    private fun assertionString(): String {
        return if (complex) {
            "!(arg instanceof ${jsType})"
        } else {
            "typeof arg !== \"${jsType}\""
        }
    }
}