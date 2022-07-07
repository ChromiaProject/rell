package net.postchain.rell.plugin

import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.app.util.LanguageSupport
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.kotlin.KotlinDocumentFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

interface RellGenPluginExtension {
    val source: DirectoryProperty
    val target: DirectoryProperty
    val language: Property<LanguageSupport>
    val packageName: Property<String>
    val mainModule: Property<String>
}

abstract class RellGenTask : DefaultTask() {
    @get:InputDirectory
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val target: DirectoryProperty

    @get:Input
    abstract val language: Property<LanguageSupport>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val moduleName: Property<String>

    @TaskAction
    fun generateCode() {
        val factory = when (language.get()) {
            LanguageSupport.Kotlin -> KotlinDocumentFactory(packageName.get())
            else -> throw GradleException("Invalid language")
        }
        val generator = CodeGenerator(factory, false)
        val sections = generator.createSections(source.get().asFile, moduleName.get())
        val documents = generator.constructDocuments(sections, true)
        DocumentSaver(target.get().asFile).saveDocuments(documents)
    }

}

class RellGenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("rellgen", RellGenPluginExtension::class.java)
            .apply {
                language.set(LanguageSupport.Kotlin)
                target.set(File(project.buildDir, "generated"))
                source.set(File(project.projectDir, "src/main/rell"))
            }

        project.tasks.register("rellgen", RellGenTask::class.java) {
            it.source.set(extension.source)
            it.target.set(extension.target)
            it.language.set(extension.language)
            it.moduleName.set(extension.mainModule)
            it.packageName.set(extension.packageName)
        }
    }
}
