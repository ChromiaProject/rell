@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.moduledocs

import org.jetbrains.dokka.base.transformers.documentables.ModuleAndPackageDocumentationTransformer
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.PreMergeDocumentableTransformer

fun RellModuleAndPackageDocumentationTransformer(context: DokkaContext): PreMergeDocumentableTransformer =  ModuleAndPackageDocumentationTransformer(RellModuleAndPackageDocumentationReader(context))
