@file:Suppress("UnstableApiUsage")

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
    jacocoAggregation(projects.rellToolbox.languageServer)
    jacocoAggregation(projects.rellCodegen.rellgen)
    jacocoAggregation(projects.rellDokkaPlugin)
}

reporting.reports.create<JacocoCoverageReport>("testCodeCoverageReport") {
    testSuiteName = "test"

    reportTask {
        reports {
            xml.required = true
            html.required = true
            csv.required = true
        }
    }
}
