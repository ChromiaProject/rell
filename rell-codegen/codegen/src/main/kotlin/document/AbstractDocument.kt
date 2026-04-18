/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.document

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.DocumentSection

abstract class AbstractDocument(
        override val intro: String = "",
        protected val module: String
) : Document {

    private val sections = mutableSetOf<DocumentSection>()

    override val packageString: String
        get() = formatPackageString()
    abstract fun formatPackageString(): String
    abstract fun formatImportString(className: ClassName): String

    private fun collectImports(): Set<String> {
        return sections.flatMap { it.imports }.toSet() +
                sections.flatMap {
                    it.deps
                            .filter { d -> d.module != module }
                            .map { c -> formatImportString(c) }
                }.toSet()
    }

    final override fun format(): String {
        val introSection = "$intro\n\n"
        val packageSection = "${formatPackageString()}\n\n"
        val importSection = "${collectImports().sorted().joinToString("\n")}\n\n"
        val methodSections = "${sections.map { it.format() }.toSet().joinToString("\n\n")}\n"

        return StringBuilder()
                .append(introSection)
                .append(packageSection.ifBlank { "" })
                .append(importSection.ifBlank { "" })
                .append(methodSections)
                .toString()
    }

    override fun addSection(section: DocumentSection) {
        sections.add(section)
    }
}