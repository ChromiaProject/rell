package net.postchain.rell.toolbox.lsp.template

import com.chromia.build.tools.template.TemplateFactoryProvider
import com.chromia.build.tools.template.TemplateProject
import java.io.File

class NewProjectTemplateService {
    fun getAvailableTemplates(): List<NewProjectTemplate> = TemplateProject.entries.map {
        NewProjectTemplate(it.name, it.displayName)
    }

    fun createNewProjectTemplate(template: TemplateProject, projectName: String, targetDir: File): File {
        val projectDir = targetDir.resolve(projectName)
        check(!projectDir.exists()) {
            "There already exist a directory called \"$projectName\" in the working directory, aborting."
        }
        projectDir.mkdirs()

        val factory = TemplateFactoryProvider.getFactory(template)
        factory.createProjectFromTemplate(projectDir, projectName)
        return projectDir
    }
}
