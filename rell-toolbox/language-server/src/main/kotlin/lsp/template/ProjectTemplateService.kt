/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists

class ProjectTemplateService(private val templateRepository: TemplateRepository = RemoteTemplateRepository()) {
    fun getAvailableTemplates(): List<NewProjectTemplate> = TemplateProject.entries.map {
        NewProjectTemplate(it.name, it.displayName)
    }

    fun createNewProjectTemplate(
        template: TemplateProject,
        projectName: String,
        targetDir: Path,
        options: TemplateOptions? = null,
    ): Path {
        val projectDir = targetDir / projectName

        check(!projectDir.exists()) {
            "There already exist a directory called \"$projectName\" in the working directory, aborting."
        }

        projectDir.createDirectories()

        val factory = TemplateFactoryProvider.getFactory(template, templateRepository.templatesRoot())
        factory.createProjectFromTemplate(projectDir, projectName, options)
        return projectDir
    }

    fun addToProject(targetDir: Path, options: TemplateOptions) {
        if (options.includeDevContainer) {
            DevContainerSupport(templateRepository.templatesRoot()).addToProject(targetDir)
        }
    }
}
