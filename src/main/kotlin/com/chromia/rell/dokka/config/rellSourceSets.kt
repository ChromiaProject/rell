package com.chromia.rell.dokka.config

import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.Platform
import java.io.File

fun rellSourceSets(projectRoot: File, includes: List<File>) = listOf(DokkaSourceSetImpl(
        sourceRoots = setOf(projectRoot),
        sourceSetID = DokkaSourceSetID("main", "dapp"),
        displayName = "dapp",
        analysisPlatform = Platform.wasm,
        includes = includes.toSet(),
        externalDocumentationLinks = setOf(systemLibExternalDocumentationLink)
))
