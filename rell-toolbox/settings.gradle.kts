import org.apache.tools.ant.DirectoryScanner

DirectoryScanner.removeDefaultExclude("**/.gitignore")

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
include("seeder")
