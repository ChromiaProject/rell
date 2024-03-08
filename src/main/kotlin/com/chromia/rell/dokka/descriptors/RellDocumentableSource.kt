@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.chromia.rell.dokka.descriptors

import org.jetbrains.dokka.analysis.kotlin.descriptors.compiler.configuration.DescriptorDocumentableSource
import org.jetbrains.dokka.model.DocumentableSource

val NULL_DESCRIPTOR: DocumentableSource = DescriptorDocumentableSource(RellDeclarationDescriptor())
