package net.postchain.rell.codegen.javascript

import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.util.BuiltinType


enum class JavascriptBuiltinType(val builtin: TypeAssertion) : BuiltinType {
    NullAssertion(NullAssertionJs),
    BooleanAssertion(BooleanAssertionJs),
    NumberAssertion(NumberAssertionJs),
    StringAssertion(StringAssertionJs),
    ObjectAssertion(ObjectAssertionJs),
    BufferAssertion(BufferAssertionJs),
    SetAssertion(SetAssertionJs),
    ArrayAssertion(ArrayAssertionJs),
    ;

    override fun createBuiltin() = builtin
}

object NullAssertionJs : TypeAssertion("assertNull", "null")

object BooleanAssertionJs : TypeAssertion("assertBoolean", "boolean")

object NumberAssertionJs : TypeAssertion("assertNumber", "number")

object StringAssertionJs : TypeAssertion("assertString", "string")

object ObjectAssertionJs : TypeAssertion("assertObject", "object")

object BufferAssertionJs : TypeAssertion("assertBuffer", "Buffer", true)
object SetAssertionJs : TypeAssertion("assertSet", "Set", true)

object ArrayAssertionJs : TypeAssertion("assertArray", "Array", true)

abstract class TypeAssertion(val functionName: String, private val jsType: String, private val complex: Boolean = false) : Builtin {

    override val moduleName = ""
    override val imports: List<String>
        get() = listOf("")

    override fun format() = """
        |function $functionName(arg) {
        |${"\t"}if(${assertionString()}) throw new Error("Expected input to be $jsType")
        |}
    """.trimMargin()

    private fun assertionString(): String {
        return if (complex) {
            "!(arg instanceof ${jsType})"
        } else {
            "typeof arg !== \"${jsType}\""
        }
    }
}