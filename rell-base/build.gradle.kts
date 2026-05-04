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
    systemProperty("rell.test.backend", findProperty("rellTestBackend") ?: "interpreter")
}

val testRoundTrip by tasks.registering(Test::class) {
    description = "Runs tests with RR serialization round-trip enabled"
    group = "verification"
    useJUnitPlatform()
    // Custom Test tasks default to `sourceSets.test`, but Gradle 8+ doesn't auto-discover
    // test classes when the task is registered (vs inherited from `test`). Explicitly point at
    // the test source set so the run isn't NO-SOURCE.
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("rell.test.roundtrip", "true")
    systemProperty("rell.test.backend", "interpreter")
    shouldRunAfter(tasks.test)
}

// Detect whether the Gradle daemon itself is running on a GraalVM-flavoured JDK.
// We use the daemon JVM directly (no toolchain pin) because:
//   - Gradle's vendor-matching auto-detection is fragile across GraalVM distributions
//     (Oracle GraalVM reports `java.vendor=Oracle Corporation`, so `matching("GraalVM")`
//     misses it), and there's no toolchain download repository configured.
//   - Pinning a `javaLauncher` Provider that fails to resolve breaks configuration cache
//     serialization, not just task execution.
// On non-GraalVM daemons (e.g. Temurin on a dev laptop) we skip the task rather than
// crash — `-XX:+UseJVMCINativeLibrary` is fatal without libgraal.
val runningOnGraalVm = arrayOf("java.vm.name", "java.vendor.version", "java.runtime.name")
    .any { System.getProperty(it, "").contains("GraalVM", ignoreCase = true) }

val testTruffle by tasks.registering(Test::class) {
    description = "Runs tests through the Truffle peer backend (Tf_Backend)"
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("rell.test.roundtrip", "false")
    systemProperty("rell.test.backend", "truffle")

    enabled = runningOnGraalVm

    if (runningOnGraalVm) {
        // Force GraalVM Truffle runtime to engage instead of fallback Interpreted runtime
        jvmArgs(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+EnableJVMCI",
            "-XX:+UseJVMCINativeLibrary",
            "--enable-native-access=ALL-UNNAMED",
        )
    }

    // Disable JaCoCo: bytecode agent races libgraal initialisation on the worker JVM.
    extensions.configure(JacocoTaskExtension::class.java) {
        isEnabled = false
    }

    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(testRoundTrip)
    dependsOn(testTruffle)
}

dependencies {
    api(projects.rellBase.frontend)
    api(projects.rellBase.runtimeInterpreter)

    testImplementation(projects.rellBase.testUtils)
    // Truffle peer backend is only referenced by Tf_BackendActivationTest; keep the dep in
    // testImplementation rather than exposing it on the production classpath.
    testImplementation(projects.rellBase.runtimeTruffle)
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
