/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.AbstractDocument
import net.postchain.rell.codegen.kotlin.util.kotlinPackage

class KotlinDocument(val packageName: String, moduleName: String) : AbstractDocument(
    "// $FILE_COMMENT",
    moduleName
) {
    override fun formatPackageString() = "package ${kotlinPackage(packageName, module)}"
    override fun formatImportString(className: ClassName) = "import ${className.toPackageName(packageName)}"
}
