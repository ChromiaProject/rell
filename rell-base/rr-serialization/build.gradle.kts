/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

import java.net.URI
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell RR serialization: FlatBuffers serialization of RR_App for IR transport"

// flatc provisioning

val flatcVersion = "v25.2.10"

val flatcPlatform: String? = run {
    val os = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    when {
        os.startsWith("Mac") && arch == "aarch64" -> "Mac"
        os.startsWith("Mac") && arch == "x86_64" -> "MacIntel"
        os.startsWith("Linux") -> "Linux"
        else -> null
    }
}

val flatcArchiveName: String? = flatcPlatform?.let {
    when (it) {
        "Linux" -> "Linux.flatc.binary.clang++-18"
        else -> "$it.flatc.binary"
    }
}

val flatcHome = layout.buildDirectory.dir("flatc").get().asFile
val flatcBin = File(flatcHome, "flatc")

val provisionFlatc by tasks.registering {
    description = "Downloads the flatc compiler"
    group = "flatbuffers"

    val outputDir = layout.buildDirectory.dir("flatc")
    outputs.dir(outputDir)

    val bin = flatcBin
    val archiveName = flatcArchiveName
    val version = flatcVersion

    onlyIf { !bin.exists() }

    doLast {
        val archive = checkNotNull(archiveName) { "No prebuilt flatc for this platform. Install flatc manually." }
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val archiveFile = File(dir, "$archive.zip")
        val url = "https://github.com/google/flatbuffers/releases/download/$version/$archive.zip"

        if (!archiveFile.exists()) {
            logger.lifecycle("Downloading flatc ($archive)...")
            URI(url).toURL().openStream().use { input ->
                archiveFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        logger.lifecycle("Extracting flatc...")
        ZipFile(archiveFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = File(dir, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        archiveFile.delete()
        bin.setExecutable(true)
        logger.lifecycle("flatc provisioned at: ${bin.absolutePath}")
    }
}

// FlatBuffers code generation

val fbsDir = layout.projectDirectory.dir("src/main/flatbuffers")
val generatedKotlinDir = layout.buildDirectory.dir("generated/flatbuffers/kotlin")

val fbsRootSchema = fbsDir.file("app.fbs")

val generateFlatBuffersKotlin by tasks.registering {
    description = "Generates Kotlin code from FlatBuffers schemas"
    group = "flatbuffers"
    dependsOn(provisionFlatc)

    val fbsSrc = fbsDir
    val rootSchema = fbsRootSchema
    val outDir = generatedKotlinDir
    val flatc = flatcBin

    inputs.dir(fbsSrc)
    outputs.dir(outDir)

    doLast {
        val out = outDir.get().asFile.apply { deleteRecursively(); mkdirs() }

        val exitCode = ProcessBuilder(
            flatc.absolutePath, "--kotlin", "--gen-all",
            "-o", out.absolutePath,
            rootSchema.asFile.absolutePath,
        )
            .directory(fbsSrc.asFile)
            .inheritIO()
            .start()
            .waitFor()
        if (exitCode != 0) error("flatc --kotlin failed (exit $exitCode)")

        // Fix flatc Kotlin codegen nullability bug: union accessors return Table,
        // but the body returns null when offset is 0. Patch return type to Table?.
        out.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                val patched = text
                    .replace(") : Table {", ") : Table? {")
                    .replace(") : Table{", ") : Table? {")
                if (patched != text) {
                    file.writeText(patched)
                }
            }
    }
}

sourceSets.main {
    kotlin.srcDir(generatedKotlinDir)
}

tasks.compileKotlin {
    dependsOn(generateFlatBuffersKotlin)
}

tasks.sourcesJar {
    dependsOn(generateFlatBuffersKotlin)
}

kotlin.compilerOptions {
    optIn.add("kotlin.ExperimentalUnsignedTypes")
}

dependencies {
    implementation(projects.rellBase.rrTree)
    implementation(libs.flatbuffers.java)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(kotlin("test-junit5"))
    testImplementation(projects.rellBase)
    testImplementation(projects.rellBase.testUtils)
}

tasks.test {
    useJUnitPlatform()
}
