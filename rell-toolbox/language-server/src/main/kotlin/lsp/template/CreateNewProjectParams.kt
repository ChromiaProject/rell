/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

data class CreateNewProjectParams(
    val template: TemplateProject,
    val projectName: String,
    val targetDirUri: String,
    val options: TemplateOptions? = null,
)

data class AddToProjectParams(
    val targetDirUri: String,
    val options: TemplateOptions,
)
