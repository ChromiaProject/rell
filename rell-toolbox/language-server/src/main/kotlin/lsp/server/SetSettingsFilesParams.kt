/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

/**
 * Parameters of `rell/setSettingsFiles`: the Chromia settings files the client has chosen, as `file:`
 * URIs — the runtime equivalent of the `chromiaConfigFiles` initialization option.
 *
 * Only files that differ from the `chromia.yml` of their own directory need listing; the server
 * discovers those by name. An empty (or absent) list therefore means "use name-based discovery
 * everywhere", which is what switching a directory back to its `chromia.yml` amounts to.
 */
internal data class SetSettingsFilesParams(
    val configFileUris: List<String>? = null,
)
