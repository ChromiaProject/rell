/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.util

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

fun net.postchain.rell.toolbox.common.Location.toLspLocation(): Location {
    return Location(this.uri, this.range.toLspRange())
}

fun net.postchain.rell.toolbox.common.Range.toLspRange(): Range {
    return Range(this.start.toLspPosition(), this.end.toLspPosition())
}

fun net.postchain.rell.toolbox.common.Position.toLspPosition(): Position {
    return Position(this.line, this.character)
}
