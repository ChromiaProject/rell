/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import com.chromia.build.tools.template.DevContainerSupport
import com.chromia.build.tools.template.TemplateFactoryProvider
import com.chromia.build.tools.template.TemplateOptions
import com.chromia.build.tools.template.TemplateProject
import java.io.File

class ProjectTemplateService {

    fun getAvailableTemplates(): List<NewProjectTemplate> = TemplateProject.entries.map {
        NewProjectTemplate(it.name, it.displayName)
    }

    fun createNewProjectTemplate(
        template: TemplateProject,
        projectName: String,
        targetDir: File,
        options: TemplateOptions? = null
    ): File {
        val projectDir = targetDir.resolve(projectName)
        check(!projectDir.exists()) {
            "There already exist a directory called \"$projectName\" in the working directory, aborting."
        }
        projectDir.mkdirs()

        val factory = TemplateFactoryProvider.getFactory(template)

        factory.createProjectFromTemplate(projectDir, projectName, options)
        return projectDir
    }

    fun addToProject(targetDir: File, options: TemplateOptions) {
        if (options.includeDevContainer) {
            DevContainerSupport().addToProject(targetDir)
        }
        // This method can be implemented to add dev container support if needed.
        // Currently, it is a placeholder for future implementation.
    }
}
