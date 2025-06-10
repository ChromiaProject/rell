package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.rTypeToPythonType

open class DataTypeSection(private val className: ClassName,
                          attributes: Map<String, R_Type>,
                          override val docSymbol: DocSymbol) : DocumentSection {
    override val moduleName get() = className.module

    override val imports: List<String> = listOf("")

    private val typeFields = attributes.map { formatAttribute(it.key, it.value) }

    private val initAssignments =
        attributes.entries
            .joinToString("\n") {
                formatAttribute(it.key, it.value)
            }

    private fun formatAttribute(name: String, type: R_Type) = "$name: ${rTypeToPythonType(type)}"

    override val deps = DependencyFinder.findDependencies(attributes.values)

    override fun format() = """
        |${PythonDocGenerator.formatDoc(docSymbol, wrapInDocComments = true)}
        |@dataclass(frozen=True)
        |class ${className.className}:
        |${initAssignments.lines().joinToString("\n") { "\t$it" }}
        |
        |${toDict().lines().joinToString("\n") { "\t$it"}}
        |
        |${fromDict().lines().joinToString("\n") { "\t$it" }}
    """.trimMargin()

    private fun toDict() = """
        |def to_dict(self) -> Dict[str, Any]:
        |   ${"\"\"\"Create a dictionary representation of the object\"\"\""}
        |   return {${typeFields.joinToString(", ") { "\"${it.substringBefore(":").lowercase()}\": self.${it.substringBefore(":").lowercase()}" }}}
    """.trimMargin()


    private fun fromDict() = """
        |@classmethod
        |def from_dict(cls, data: Dict[str, Any]) -> '${className.className}':
        |   ${"\"\"\"Create an object from a dictionary\"\"\""}
        |   return cls(${typeFields.joinToString(", ") { "data[\"${it.substringBefore(":").lowercase()}\"]" }})
    """.trimMargin()
}