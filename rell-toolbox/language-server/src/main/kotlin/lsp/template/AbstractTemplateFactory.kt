/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path

abstract class AbstractTemplateFactory(protected val templatesRoot: Path) : TemplateFactory {

    override fun createProjectFromTemplate(targetDir: Path, projectName: String, options: TemplateOptions?) {
        if (options?.includeDevContainer == true) {
            DevContainerSupport(templatesRoot).addToProject(targetDir)
        }
        createProjectFiles(targetDir, projectName, options)
    }

    abstract fun createProjectFiles(targetDir: Path, projectName: String, options: TemplateOptions?)
}
