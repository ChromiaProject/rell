/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.compose

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Parses the user-authored `# Dapp <name>` / `# Module <name>` / `# Package <qname>` blocks
 * embedded in includes (`.md` files passed via `--includes`).
 *
 * The historical Rell-dokka-plugin rewrote `# Dapp` → `# Module` and `# Module` → `# Package`
 * before handing the file to Dokka so it could reuse Dokka's `ModuleAndPackageDocumentation`
 * parser. Without Dokka in the loop we keep the user-facing vocabulary directly: `# Dapp` is the
 * site title's overview, `# Module` is one of the compiled `R_Module`s, `# Package` is a nested
 * namespace within one of those modules. The renderer attaches the descriptions to the
 * matching `Doc_Package.docMd` or `Doc_Module.docMd`.
 *
 * `# Package [root]` is keyed under `[root]` so the system-lib root package can pick it up.
 */
internal class ModuleDocs(
    /** Keyed on the dapp title (or "Rell System Library API Reference"). */
    private val moduleDocs: Map<String, String>,
    /** Keyed on the package qualified name (or `[root]`). */
    private val packageDocs: Map<String, String>,
) {
    fun moduleDoc(title: String): String = moduleDocs[title].orEmpty()
    fun packageDoc(qname: String): String? = packageDocs[qname.ifEmpty { "[root]" }]

    companion object {
        fun load(files: List<Path>, additionalTexts: List<String> = emptyList()): ModuleDocs {
            val moduleDocs = mutableMapOf<String, String>()
            val packageDocs = mutableMapOf<String, String>()
            val sources = files.asSequence()
                .filter { it.isRegularFile() }
                .map { it.readText() }
                .plus(additionalTexts.asSequence())
            for (text in sources) {
                for (fragment in parseFragments(text)) {
                    when (fragment.kind) {
                        FragmentKind.DAPP, FragmentKind.MODULE -> {
                            val cur = moduleDocs[fragment.name]
                            moduleDocs[fragment.name] = if (cur.isNullOrBlank()) fragment.body else "$cur\n\n${fragment.body}"
                        }
                        FragmentKind.PACKAGE -> {
                            val cur = packageDocs[fragment.name]
                            packageDocs[fragment.name] = if (cur.isNullOrBlank()) fragment.body else "$cur\n\n${fragment.body}"
                        }
                    }
                }
            }
            return ModuleDocs(moduleDocs, packageDocs)
        }

        /**
         * Read the bundled `rell.md` resource shipped with the plugin (system-lib package
         * summaries). Returns `null` if the resource is missing (defensive — the file lives
         * under `src/main/resources/rell.md` so it's always packaged).
         */
        fun loadBundledSystemDocsText(): String? =
            ModuleDocs::class.java.classLoader
                .getResourceAsStream("rell.md")
                ?.use { it.bufferedReader().readText() }

        private val FRAGMENT_HEADER = Regex(
            "(^|\\R)#\\s+(Dapp|Module|Package)\\s+([^\\r\\n]+?)\\s*(\\R|$)",
        )

        private enum class FragmentKind { DAPP, MODULE, PACKAGE }
        private data class Fragment(val kind: FragmentKind, val name: String, val body: String)

        private fun parseFragments(text: String): List<Fragment> {
            val matches = FRAGMENT_HEADER.findAll(text).toList()
            if (matches.isEmpty()) return emptyList()
            val out = mutableListOf<Fragment>()
            for ((i, m) in matches.withIndex()) {
                val kind = when (m.groupValues[2]) {
                    "Dapp" -> FragmentKind.DAPP
                    "Module" -> FragmentKind.MODULE
                    "Package" -> FragmentKind.PACKAGE
                    else -> continue
                }
                val name = m.groupValues[3].trim()
                val bodyStart = m.range.last + 1
                val bodyEnd = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
                val body = text.substring(bodyStart, bodyEnd).trim()
                out += Fragment(kind, name, body)
            }
            return out
        }
    }
}
