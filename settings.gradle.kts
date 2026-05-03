@file:Suppress("UnstableApiUsage")

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
        maven("https://gitlab.com/api/v4/projects/64941451/packages/maven") {
            name = "chromia-cli-tools"
        }
        maven("https://gitlab.com/api/v4/projects/46288950/packages/maven") {
            name = "chromia-misc"
        }
        maven("https://gitlab.com/api/v4/projects/32802097/packages/maven") {
            name = "rell"
        }
    }
}

include("rell-base")
include("rell-base:utils")
include("rell-base:rr-tree")
include("rell-base:frontend")
include("rell-base:runtime-core")
include("rell-base:runtime-interpreter")
include("rell-base:test-utils")
include("rell-api-base")
include("rell-api-gtx")
include("rell-api-native")
include("rell-api-shell")
include("rell-gtx")
include("rell-tools")
include("coverage-report-aggregate")

include("rell-toolbox:common")
include("rell-toolbox:ast")
include("rell-toolbox:indexer")
include("rell-toolbox:code-quality")
include("rell-toolbox:language-server")
include("rell-toolbox:seeder")

include("rell-codegen:codegen")
include("rell-codegen:codegen-kotlin")
include("rell-codegen:codegen-typescript")
include("rell-codegen:codegen-javascript")
include("rell-codegen:codegen-python")
include("rell-codegen:codegen-mermaid")
include("rell-codegen:rellgen")

include("rell-dokka-plugin")
include("rell-base:rr-serialization")

include("benchmarks")
