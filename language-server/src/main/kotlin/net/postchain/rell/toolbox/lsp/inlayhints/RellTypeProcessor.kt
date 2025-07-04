package net.postchain.rell.toolbox.lsp.inlayhints

object RellTypeProcessor {

    private fun isTuple(content: String) = content.matches(Regex("\\(.*\\)\\??"))

    // NOTE: this class used mainly to strip param names in tuples
    //  because `DocSymbol.declaration.completion.result return tuple types with their params
    //  e.g: (a: (aa:text,bb:integer), b: integer) =====> ((text, integer), integer)
    fun processType(type: String): String = when {
        type.startsWith("(") && type.contains("->") -> type
        isTuple(type) -> processTuple(type)
        "<" in type -> processGenericType(type)
        else -> type
    }

    private fun processTuple(tuple: String): String {
        val isOptional = tuple.endsWith(")?")
        val content = if (isOptional) {
            tuple.drop(1).dropLast(2)
        } else {
            tuple.drop(1).dropLast(1)
        }

        val parts = splitContent(content)
        val processedParts = parts.map { part ->
            val trimmed = part.trim()
            when {
                isTuple(trimmed) -> processTuple(trimmed)
                trimmed.contains(":") -> {
                    val typePart = trimmed.substringAfter(":").trim()
                    processType(typePart)
                }
                else -> processType(trimmed)
            }
        }

        val result = "(${processedParts.joinToString(", ")})"
        return if (isOptional) "$result?" else result
    }

    private fun processGenericType(type: String): String {
        val openIndex = type.indexOf('<')
        val closeIndex = type.lastIndexOf('>')
        val isOptional = type.endsWith("?")

        if (openIndex == -1 || closeIndex == -1 || closeIndex <= openIndex) {
            return type
        }

        val baseType = type.substring(0, openIndex)
        val genericContent = type.substring(openIndex + 1, closeIndex)

        val genericParams = splitContent(genericContent)
        val processedParams = genericParams.map { param -> processType(param.trim()) }

        val result = "$baseType<${processedParams.joinToString(", ")}>"
        return if (isOptional) "$result?" else result
    }

    private fun splitContent(content: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0
        var angleDepth = 0

        for (i in content.indices) {
            when (val c = content[i]) {
                '(' -> {
                    current.append(c)
                    parenDepth++
                }
                ')' -> {
                    current.append(c)
                    parenDepth--
                }
                '<' -> {
                    current.append(c)
                    angleDepth++
                }
                '>' -> {
                    current.append(c)
                    angleDepth--
                }
                ',' -> {
                    if (parenDepth == 0 && angleDepth == 0) {
                        parts.add(current.toString().trim())
                        current.clear()
                    } else {
                        current.append(c)
                    }
                }
                else -> current.append(c)
            }
        }

        if (current.isNotEmpty()) {
            parts.add(current.toString().trim())
        }

        return parts
    }
}
