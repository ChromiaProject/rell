package net.postchain.rell.codegen.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupSwitch
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.CodeGeneratorConfig
import net.postchain.rell.codegen.MermaidCodeGeneratorConfig
import net.postchain.rell.codegen.MermaidDocumentFactory
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.javascript.JavascriptCodeGeneratorConfig
import net.postchain.rell.codegen.javascript.JavascriptDocumentFactory
import net.postchain.rell.codegen.kotlin.KotlinCodeGeneratorConfig
import net.postchain.rell.codegen.kotlin.KotlinDocumentFactory
import net.postchain.rell.codegen.python.PythonCodeGeneratorConfig
import net.postchain.rell.codegen.python.PythonDocumentFactory
import net.postchain.rell.codegen.typescript.TypescriptCodeGeneratorConfig
import net.postchain.rell.codegen.typescript.TypescriptDocumentFactory


sealed class LanguageOption(language: String): CodeGeneratorConfig, OptionGroup(language) {
}
class KotlinOptionGroup: KotlinCodeGeneratorConfig, LanguageOption("Kotlin") {
    private val packageNam by option("--package", help = "Name of package").required()
    override fun packageName() = packageNam
}

class MermaidOption: MermaidCodeGeneratorConfig, LanguageOption("Mermaid") {
    private val mdx by option(help = "Surround with mdx tags").flag()
    private val er  by option("--entity-relation", help = "Generate entity relation diagram or class diagram").flag("--class-diagram", default = true)
    override fun mdx() = mdx
    override fun erDiagram() = er
}

class TypescriptOption: TypescriptCodeGeneratorConfig, LanguageOption("Typescript")
class PythonOption: PythonCodeGeneratorConfig, LanguageOption("Python")
class JavscriptOption: JavascriptCodeGeneratorConfig, LanguageOption("Typescript")

class CodeGenCommand : CliktCommand("Generates files based on rell sources") {

    private val source by argument("source", "Source folder").file()
    private val target by argument("target", "Target folder").file(canBeFile = false, canBeDir = true)

    private val moduleName by option("--module", help = "Module name").split(",")

    private val language by option(help = "Language to generate for").groupSwitch(
            "--kotlin" to KotlinOptionGroup(),
            "--mermaid" to MermaidOption(),
            "--typescript" to TypescriptOption(),
            "--javascript" to JavscriptOption(),
            "--python" to PythonOption(),
    ).required()

    override fun run() {
        val factory = when (language) {
            is KotlinOptionGroup -> KotlinDocumentFactory(language as KotlinCodeGeneratorConfig)
            is JavscriptOption -> JavascriptDocumentFactory()
            is TypescriptOption -> TypescriptDocumentFactory()
            is MermaidOption -> MermaidDocumentFactory(language as MermaidCodeGeneratorConfig)
            is PythonOption -> PythonDocumentFactory()
        }
        val generator = CodeGenerator(factory, language)
        val sections = generator.createSections(source, moduleName)
        val documents = generator.constructDocuments(sections)
        DocumentSaver(target).saveDocuments(documents)
        echo("Created files: ${documents.keys}")
    }
}
