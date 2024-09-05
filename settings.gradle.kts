pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "rell-toolbox"

include("common")
include("language-server")
include("code-quality")
include("indexer")
include("ast")
