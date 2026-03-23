/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer.references

import net.postchain.rell.base.utils.ide.IdeSymbolId
import java.net.URI

data class GlobalReference(val fileUri: URI, val symbolId: IdeSymbolId)
