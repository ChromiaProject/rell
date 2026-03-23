/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.moduledocs

import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.base.transformers.documentables.ModuleAndPackageDocumentationTransformer
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.PreMergeDocumentableTransformer

@OptIn(InternalDokkaApi::class)
fun rellModuleAndPackageDocumentationTransformer(context: DokkaContext): PreMergeDocumentableTransformer =
        ModuleAndPackageDocumentationTransformer(RellModuleAndPackageDocumentationReader(context))
