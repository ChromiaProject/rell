/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.transformers

import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.transformers.pages.PageTransformer

class NullPageTransformer: PageTransformer {
    override fun invoke(input: RootPageNode): RootPageNode {
        return input
    }
}