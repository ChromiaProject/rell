/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.config

import com.chromia.rell.doc.model.Doc_SourceLink
import net.postchain.rell.api.base.RellCliEnv
import java.nio.file.Path

/**
 * Resolved configuration for one doc-generator run. Plain POJO — no Dokka types.
 *
 * `RellDokkaGenerator` reads this directly; chromia-cli uses the [RellDokkaPluginConfigurationBuilder]
 * to assemble it (preserving the prior public surface).
 *
 * Filesystem inputs are stored as `java.nio.file.Path` (the builder accepts the public-API
 * `java.io.File` form and converts at the boundary).
 */
internal class RellDokkaPluginConfiguration(
    val system: Boolean,
    val title: String,
    val modules: List<String>?,
    val projectRoot: Path?,
    val targetFolder: Path,
    val customStyleSheets: List<Path>,
    val customAssets: List<Path>,
    val footerMessage: String,
    val includes: List<Path>,
    val sourceLinks: List<Doc_SourceLink>,
    val filteredModules: List<String>,
    val additionalModules: List<String>,
    val cliEnv: RellCliEnv?,
) {
    companion object {
        const val SYSTEM_TITLE: String = "Rell System Library API Reference"

        /**
         * Module name used for the system-lib *output directory slug* — distinct from [SYSTEM_TITLE]
         * (which is the page `<title>`/H1). The legacy Dokka generator named its `DModule`
         * "Rell System Library", producing the `-rell -system -library` directory that
         * docs.chromia.com and downstream tooling already link to. Deriving the slug from the
         * longer title would silently break every existing URL, so the slug stays pinned here.
         */
        const val SYSTEM_MODULE_NAME: String = "Rell System Library"
    }
}
