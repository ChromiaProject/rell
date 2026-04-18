/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.config

import org.jetbrains.dokka.ExternalDocumentationLinkImpl
import java.net.URI

val systemLibExternalDocumentationLink = ExternalDocumentationLinkImpl(
        URI("https://docs.chromia.com/pages/rell/").toURL(),
        URI("https://docs.chromia.com/pages/rell/-rell%20-system%20-library/package-list").toURL()
)
