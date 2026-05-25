/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.model

import java.net.URL
import java.nio.file.Path

/**
 * Whole documentation site: one or more `Doc_Module`s plus site-wide options that the renderer
 * consumes verbatim (custom CSS/asset file paths, source-link rewrites, the footer string).
 *
 * `Doc_Site` is the only thing the renderer takes; everything Dokka-side previously threaded
 * through `DokkaConfiguration` lands here as plain data so the renderer can be tested in
 * isolation from the compiler driver.
 */
internal data class Doc_Site(
    val title: String,
    val footerMessage: String,
    val modules: List<Doc_Module>,
    val customStyleSheets: List<Path>,
    val customAssets: List<Path>,
    val sourceLinks: List<Doc_SourceLink>,
    val hiddenPackages: Set<String>,
    val system: Boolean,
)

/** Maps an on-disk Rell file (under `localDirectory`) to a remote `url` + line-number suffix. */
internal data class Doc_SourceLink(
    val localDirectory: Path,
    val remoteUrl: URL,
    val remoteLineSuffix: String?,
)

/**
 * One "module" in the site — a top-level grouping that becomes a directory under the site root.
 * For the system library this is "Rell System Library API Reference" (`-rell -system -library -a-p-i -reference`).
 * For an application this is the dapp title (e.g. "My Dapp" → `-my -dapp`).
 *
 * `slug` is the on-disk directory name; we keep it on the model so the renderer doesn't need to
 * know the slug-mangling rules a second time.
 */
internal data class Doc_Module(
    val name: String,
    val slug: String,
    val docMd: String,
    val packages: List<Doc_Package>,
    val system: Boolean,
)

/**
 * One Rell module or namespace within a `Doc_Module`. `qname` is the fully-qualified Rell name
 * ("" for the root namespace, e.g. "main", "lib.lib1", "lib.lib1.nested", "rell.test").
 *
 * The renderer files a `Doc_Package` at `<moduleSlug>/<qname-or-[root]>/index.html`.
 */
internal data class Doc_Package(
    val qname: String,
    val docMd: String,
    val defs: List<Doc_Def>,
) {
    val displayName: String get() = qname.ifEmpty { "[root]" }
}
