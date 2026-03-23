/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.jacoco.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class ExcludeFromJacocoGeneratedReport
// Annotation is used by the JaCoCo tests report generator, by adding a custom annotation to a class or function
// it will be excluded from the report as it contains the string "generated" in the class name.
