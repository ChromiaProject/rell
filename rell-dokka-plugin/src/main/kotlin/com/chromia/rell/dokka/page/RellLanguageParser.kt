/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.page

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.analysis.kotlin.internal.DocumentableLanguage
import org.jetbrains.dokka.analysis.kotlin.internal.DocumentableSourceLanguageParser
import org.jetbrains.dokka.model.Documentable


/**
 * Makes sure our page creator does not think the rell files are interpreted as kotlin nor java.
 * Not sure why they decided to make this an enum..
 * @see RellPageCreator
 */
@OptIn(InternalDokkaApi::class)
class RellLanguageParser: DocumentableSourceLanguageParser {
    @OptIn(InternalDokkaApi::class)
    override fun getLanguage(documentable: Documentable, sourceSet: DokkaConfiguration.DokkaSourceSet): DocumentableLanguage? {
        return null
    }
}