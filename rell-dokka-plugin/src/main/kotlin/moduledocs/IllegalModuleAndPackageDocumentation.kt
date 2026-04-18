
/*
 * Copyright 2014-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package com.chromia.rell.dokka.moduledocs

import org.jetbrains.dokka.DokkaException

internal class IllegalModuleAndPackageDocumentation(
        source: ModuleAndPackageDocumentationSource, message: String
) : DokkaException("[$source] $message")