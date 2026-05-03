plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.git.properties)
    jacoco
}

description = "Rell core: umbrella re-exporting frontend + runtime"

sourceSets.main {
    kotlin.setSrcDirs(emptyList<String>())
    java.setSrcDirs(emptyList<String>())
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("rell.test.roundtrip", findProperty("rellTestRoundTrip") ?: "false")
}

val testRoundTrip by tasks.registering(Test::class) {
    description = "Runs tests with RR serialization round-trip enabled"
    group = "verification"
    useJUnitPlatform()
    systemProperty("rell.test.roundtrip", "true")
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(testRoundTrip)
}

dependencies {
    api(projects.rellBase.frontend)
    api(projects.rellBase.runtimeInterpreter)

    testImplementation(projects.rellBase.testUtils)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.log4j.slf4j2.impl)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.postgresql)
}

// The gradle-git-properties plugin does not declare customProperty values as task inputs,
// so Gradle's build cache can serve stale outputs missing those properties. We collect them
// in a map and register them both as custom properties and as explicit task inputs.
val gitCustomProperties = mapOf(
    "project.groupId" to project.group.toString(),
    "project.artifactId" to project.name,
    "project.version" to project.version.toString(),
    "kotlin.version" to libs.versions.kotlin.get(),
    "postchain.version" to libs.versions.postchain.get(),
)

gitProperties {
    gitPropertiesName = "rell-base-maven.properties"
    keys = listOf(
        "git.branch",
        "git.commit.id",
        "git.commit.id.abbrev",
        "git.commit.id.describe",
        "git.commit.message.short",
        "git.commit.message.full",
        "git.commit.time",
        "git.dirty",
        "git.build.version",
    )
    for ((k, v) in gitCustomProperties) {
        customProperty(k, v)
    }
}

tasks.generateGitProperties {
    for ((k, v) in gitCustomProperties) {
        inputs.property("customProperty.$k", v)
    }
}

val generateDependencyList by tasks.registering {
    group = "build"
    description = "Generates dependency list file"

    val outputDir = layout.buildDirectory.dir("generated/resources/dependencies")
    val outputFile = outputDir.map { it.file("rell-base-dependencies.txt") }
    val artifacts = configurations.runtimeClasspath.flatMap { config ->
        config.incoming.artifacts.resolvedArtifacts.map { results ->
            results.map { "${it.id.componentIdentifier}" }.sorted()
        }
    }

    inputs.property("artifacts", artifacts)
    outputs.dir(outputDir)

    doLast {
        outputDir.get().asFile.mkdirs()
        outputFile.get().asFile.writeText(artifacts.get().joinToString("\n"))
    }
}

sourceSets.main {
    resources {
        srcDir(generateDependencyList.map { it.outputs.files.singleFile })
    }
}

tasks.processResources {
    dependsOn(generateDependencyList)
}
