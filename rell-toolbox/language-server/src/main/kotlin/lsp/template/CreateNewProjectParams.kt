/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

internal data class CreateNewProjectParams(
    val template: TemplateProject,
    val projectName: String,
    val targetDirUri: String,
    val options: TemplateOptions? = null,
)

internal data class AddToProjectParams(
    val targetDirUri: String,
    val options: TemplateOptions,
)
