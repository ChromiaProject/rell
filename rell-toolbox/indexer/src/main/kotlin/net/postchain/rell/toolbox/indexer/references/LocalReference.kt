/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer.references

import org.antlr.v4.runtime.misc.Interval
import java.net.URI

data class LocalReference(val fileUri: URI, val interval: Interval)
