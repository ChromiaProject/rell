/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

plugins {
    base
    `jacoco-report-aggregation`
}

dependencies {
    jacocoAggregation(projects.rellBase)
    jacocoAggregation(projects.rellApiBase)
    jacocoAggregation(projects.rellApiGtx)
    jacocoAggregation(projects.rellApiNative)
    jacocoAggregation(projects.rellApiShell)
    jacocoAggregation(projects.rellGtx)
    jacocoAggregation(projects.rellTools)
}

reporting.reports.create<JacocoCoverageReport>("testCodeCoverageReport") {
    testSuiteName = "test"

    reportTask {
        reports {
            xml.required = true
            html.required = true
        }
    }
}
