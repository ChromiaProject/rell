
plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("net.postchain.rell.codegen.kotlin-common-conventions")

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

val appFile = layout.buildDirectory.file("distributions/$name-$version.tar")
val appArtifact = artifacts.add("archives", appFile.get().asFile) {
    type = "tar"
    builtBy("distTar")
}

publishing {
    publications {
        this.getByName("maven") {
            this as MavenPublication
            artifact(appArtifact)
        }
    }
}

dependencies {
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.17.1")
}
