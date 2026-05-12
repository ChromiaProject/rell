/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path

object TemplateFactoryProvider {
    fun getFactory(template: TemplateProject, templatesRoot: Path): TemplateFactory = when (template) {
        TemplateProject.PLAIN -> PlainTemplateFactory(templatesRoot)
        TemplateProject.PLAIN_MULTI -> PlainMultiTemplateFactory(templatesRoot)
        TemplateProject.MINIMAL -> MinimalTemplateFactory(templatesRoot)
        TemplateProject.PLAIN_LIBRARY -> PlainLibraryTemplateFactory(templatesRoot)
        TemplateProject.ASSET_MANAGEMENT -> AssetManagementTemplateFactory(templatesRoot)
    }
}
