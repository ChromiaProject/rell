package net.postchain.rell.toolbox.lsp.template

import com.chromia.build.tools.template.TemplateProject

data class CreateNewProjectParams(
    val template: TemplateProject,
    val projectName: String,
    val targetDirUri: String,
)
