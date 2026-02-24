/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

rootProject.name = "rell"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
        maven("https://gitlab.com/api/v4/projects/50818999/packages/maven") {
            name = "chromia-parent"
        }
        maven("https://gitlab.com/api/v4/projects/32294340/packages/maven") {
            name = "postchain"
        }
        maven("https://maven.emrld.io") {
            name = "etherjar"
        }
    }
}

include("rell-base")
include("rell-api-base")
include("rell-api-gtx")
include("rell-api-native")
include("rell-api-shell")
include("rell-gtx")
include("rell-tools")
include("coverage-report-aggregate")
