/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path

interface TemplateFactory {
    fun createProjectFromTemplate(targetDir: Path, projectName: String, options: TemplateOptions? = null)
}

data class TemplateOptions(
    val includeDevContainer: Boolean = false,
)
