package com.chromia.rell.dokka.config

import org.jetbrains.dokka.ExternalDocumentationLinkImpl
import java.net.URL

val systemLibExternalDocumentationLink = ExternalDocumentationLinkImpl(
        URL("https://docs.chromia.com/pages/rell/"),
        URL("https://docs.chromia.com/pages/rell/-rell%20-system%20-library/package-list")
)
