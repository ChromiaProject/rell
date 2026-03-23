/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.deprecation

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.Annotations
import org.jetbrains.dokka.model.BooleanValue
import org.jetbrains.dokka.model.StringValue
import java.util.Locale

fun C_Deprecated.toAnnotation() = Annotations.Annotation(
        DRI("kotlin", "Deprecated"),
        params = mapOf(
                "message" to StringValue(detailsMessage().substring(2).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }),
                "forRemoval" to BooleanValue(error),
        ), scope = Annotations.AnnotationScope.DIRECT, mustBeDocumented = true)