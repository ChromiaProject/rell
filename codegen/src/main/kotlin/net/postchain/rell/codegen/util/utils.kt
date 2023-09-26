package net.postchain.rell.codegen.util

import net.postchain.rell.codegen.CodeGenerator
import java.util.*
import net.postchain.rell.api.base.RellCliEnv

fun capitalize(name: String) =
    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
val snakeRegex = "_[a-zA-Z0-9]".toRegex()

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
    fun createAnnotation(comment: String) = "@Generated(\"${CodeGenerator::class.qualifiedName}\", comments = \"$comment\")"
}

class CachedRellCliEnv(
    private val rellCliEnv: RellCliEnv,
    private val cacheOutput: Boolean = false,
    private val cacheError: Boolean = false
) : RellCliEnv {
    val errorCache = mutableListOf<String>()
    val outputCache = mutableListOf<String>()
    override fun error(msg: String) = rellCliEnv.error(msg).also { if (cacheError) errorCache.add(msg) }
    override fun print(msg: String) = rellCliEnv.print(msg).also { if (cacheOutput) outputCache.add(msg) }
}
