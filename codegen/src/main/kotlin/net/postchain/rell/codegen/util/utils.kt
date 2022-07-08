package net.postchain.rell.codegen.util

import net.postchain.rell.codegen.CodeGenerator
import java.util.*

fun capitalize(name: String) =
    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
val snakeRegex = "_[a-zA-Z]".toRegex()

// String extensions
fun String.camelToSnakeCase(): String {
    return camelRegex.replace(this) {
        "_${it.value}"
    }.lowercase(Locale.getDefault())
}

fun String.snakeToLowerCamelCase(): String {
    return snakeRegex.replace(this) {
        it.value.replace("_","")
            .uppercase(Locale.getDefault())
    }
}

fun String.snakeToUpperCamelCase(): String {
    return capitalize(this.snakeToLowerCamelCase())
}

object GeneratedAnnotation {
    private val now = Date(System.currentTimeMillis())

    fun createAnnotation(comment: String) = "@Generated(\"${CodeGenerator::class.qualifiedName}\", comments = \"$comment\", date = \"$now\")"
}