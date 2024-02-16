package com.chromia.rell.dokka.translator

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.analysis.kotlin.internal.DocumentableLanguage
import org.jetbrains.dokka.analysis.kotlin.internal.DocumentableSourceLanguageParser
import org.jetbrains.dokka.model.Documentable

@OptIn(InternalDokkaApi::class)
class RellLanguageParser: DocumentableSourceLanguageParser {
    @OptIn(InternalDokkaApi::class)
    override fun getLanguage(documentable: Documentable, sourceSet: DokkaConfiguration.DokkaSourceSet): DocumentableLanguage? {
        return null
    }
}