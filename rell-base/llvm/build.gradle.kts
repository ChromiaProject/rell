/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Rell LLVM backend: native shared library + JNI bridge consuming the FlatBuffers-serialized RR_App."

// The native shared library is built by an Exec task wrapping clang directly, not by
// Gradle's `cpp-library` plugin. The plugin's component model collides with kotlin-jvm
// (the `library {}` extension hides the Java `JavaPluginExtension`, the `LinkSharedLibrary`
// tasks aren't registered, and the standard `test`/`dependencies` accessors disappear).
// A plain Exec keeps everything inside one module without that fight.

// --- LLVM provisioning ---

val llvmVersion = "19.1.7"

data class LlvmPlatform(val os: String, val arch: String, val archiveSuffix: String)

val llvmPlatform: LlvmPlatform? = run {
    val os = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    when {
        os.startsWith("Mac") && arch == "aarch64" ->
            LlvmPlatform("macos", "arm64", "macOS-ARM64")
        os.startsWith("Linux") && arch in listOf("amd64", "x86_64") ->
            LlvmPlatform("linux", "x64", "Linux-X64")
        else -> null
    }
}

val llvmArchiveName: String? = llvmPlatform?.let { "LLVM-$llvmVersion-${it.archiveSuffix}" }

val llvmHome: File = run {
    System.getenv("LLVM_HOME")?.let { return@run file(it) }

    val os = System.getProperty("os.name")
    if (os.startsWith("Mac")) {
        for (prefix in listOf("/opt/homebrew/opt/llvm", "/usr/local/opt/llvm")) {
            val dir = file(prefix)
            if (dir.exists()) return@run dir
        }
    }
    if (os.startsWith("Linux")) {
        for (v in 19 downTo 15) {
            val dir = file("/usr/lib/llvm-$v")
            if (dir.exists()) return@run dir
        }
    }

    llvmArchiveName?.let { layout.buildDirectory.dir("llvm/$it").get().asFile }
        ?: error("No LLVM found and no prebuilt binary for this platform. Set LLVM_HOME.")
}

val llvmConfigBin = File(llvmHome, "bin/llvm-config")
val llvmClangBin = File(llvmHome, "bin/clang++")

val provisionLlvm by tasks.registering {
    description = "Downloads and extracts LLVM $llvmVersion for the current platform"
    group = "llvm"

    val outputDir = layout.buildDirectory.dir("llvm")
    outputs.dir(outputDir)

    val configBin = llvmConfigBin
    val archiveName = llvmArchiveName
    val version = llvmVersion
    val homeDir = llvmHome

    onlyIf { !configBin.exists() }

    doLast {
        val archive = archiveName
            ?: error("No prebuilt LLVM binary for this platform. Install LLVM manually and set LLVM_HOME.")

        val dir = outputDir.get().asFile.apply { mkdirs() }
        val archiveFile = File(dir, "$archive.tar.xz")
        val url = "https://github.com/llvm/llvm-project/releases/download/llvmorg-$version/$archive.tar.xz"

        fun run(vararg cmd: String, workDir: File = dir) {
            val exitCode = ProcessBuilder(*cmd)
                .directory(workDir)
                .inheritIO()
                .start()
                .waitFor()
            if (exitCode != 0) error("Command failed (exit $exitCode): ${cmd.joinToString(" ")}")
        }

        if (!archiveFile.exists()) {
            logger.lifecycle("Downloading LLVM $version ($archive)...")
            run("curl", "-L", "--progress-bar", "-o", archiveFile.absolutePath, url)
        }

        logger.lifecycle("Extracting LLVM...")
        run("tar", "xf", archiveFile.name)

        archiveFile.delete()
        logger.lifecycle("LLVM provisioned at: ${homeDir.absolutePath}")
    }
}

// --- FlatBuffers C++ provisioning (header-only runtime) ---

val flatbuffersVersion = "v25.2.10"
val flatbuffersHeadersDir = layout.buildDirectory.dir("flatbuffers/include")

val provisionFlatBuffersHeaders by tasks.registering {
    description = "Downloads and extracts the FlatBuffers C++ runtime headers"
    group = "flatbuffers"

    val outputDir = flatbuffersHeadersDir
    val markerFile = outputDir.map { it.file("flatbuffers/flatbuffers.h") }
    outputs.file(markerFile)

    val version = flatbuffersVersion

    onlyIf { !markerFile.get().asFile.exists() }

    doLast {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val tarFile = File(dir, "flatbuffers-src.tar.gz")
        val url = "https://github.com/google/flatbuffers/archive/refs/tags/$version.tar.gz"

        logger.lifecycle("Downloading FlatBuffers C++ headers ($version)...")
        URI(url).toURL().openStream().use { input ->
            tarFile.outputStream().use { output -> input.copyTo(output) }
        }

        val exit = ProcessBuilder(
            "tar", "xzf", tarFile.absolutePath,
            "--strip-components=2",
            "-C", dir.absolutePath,
            "flatbuffers-${version.removePrefix("v")}/include"
        ).inheritIO().start().waitFor()
        check(exit == 0) { "tar extraction failed (exit $exit)" }

        tarFile.delete()
        logger.lifecycle("FlatBuffers headers at: ${dir.absolutePath}")
    }
}

// --- FlatBuffers C++ code generation ---

val rrSerialization = project(":rell-base:rr-serialization")
val fbsDir = rrSerialization.layout.projectDirectory.dir("src/main/flatbuffers")
val generatedCppDir = layout.buildDirectory.dir("generated/flatbuffers/cpp")

val generateFlatBuffersCpp by tasks.registering {
    description = "Generates C++ headers from FlatBuffers schemas in :rell-base:rr-serialization"
    group = "flatbuffers"
    dependsOn(":rell-base:rr-serialization:provisionFlatc")

    val fbsSrc = fbsDir
    val outDir = generatedCppDir
    val flatc = rrSerialization.layout.buildDirectory.file("flatc/flatc").map { it.asFile }

    inputs.dir(fbsSrc)
    outputs.dir(outDir)

    doLast {
        val out = outDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val rootSchema = fbsSrc.file("app.fbs").asFile

        val exitCode = ProcessBuilder(
            flatc.get().absolutePath, "--cpp", "--gen-all",
            "-o", out.absolutePath,
            rootSchema.absolutePath,
        )
            .directory(fbsSrc.asFile)
            .inheritIO()
            .start()
            .waitFor()
        check(exitCode == 0) { "flatc --cpp failed (exit $exitCode)" }

        // flatc unconditionally appends `EnumName_MIN = X_<first>` / `EnumName_MAX = X_<last>`
        // alias entries to every enum. For enums that literally name a value MIN or MAX (e.g.
        // ColAtFieldSummarizationKind), this produces a duplicate enumerator and clang fails to
        // compile. Strip those two trailing alias lines from every enum block.
        val aliasLine = Regex("""^\s*\w+_(MIN|MAX)\s*=\s*\w+_\w+\s*,?\s*$""")
        out.walkTopDown().filter { it.extension == "h" }.forEach { file ->
            val text = file.readText()
            val patched = text.lineSequence().filterNot { aliasLine.matches(it) }.joinToString("\n")
            if (patched != text) file.writeText(patched)
        }
    }
}

// --- JNI include path resolution ---

val jniIncludeDirs: List<File> = run {
    val javaHome = File(System.getProperty("java.home"))
    val candidates = listOf(javaHome, javaHome.parentFile).filterNotNull()
    val withInclude = candidates.firstOrNull { File(it, "include").exists() }
        ?: error("Could not locate a JDK include/ directory under ${javaHome.absolutePath}. Run on a JDK, not a JRE.")
    val include = File(withInclude, "include")
    val platform = if (System.getProperty("os.name").startsWith("Mac")) "darwin" else "linux"
    listOf(include, File(include, platform))
}

// --- Shared library build ---

val isMac = System.getProperty("os.name").startsWith("Mac")
val sharedLibName = if (isMac) "librell-llvm.dylib" else "librell-llvm.so"
val nativeOutDir = layout.buildDirectory.dir("native")
val sharedLibFile = nativeOutDir.map { it.file(sharedLibName) }

val buildNativeLibrary by tasks.registering(Exec::class) {
    description = "Compiles and links librell-llvm as a shared library via clang"
    group = "build"
    dependsOn(provisionLlvm, provisionFlatBuffersHeaders, generateFlatBuffersCpp)

    val cpp = layout.projectDirectory.file("src/main/cpp/jni_bridge.cpp")
    val outFile = sharedLibFile

    inputs.file(cpp)
    inputs.dir(generatedCppDir)
    inputs.property("llvmHome", llvmHome.absolutePath)
    outputs.file(outFile)

    doFirst {
        outFile.get().asFile.parentFile.mkdirs()
    }

    val llvmLibDir = File(llvmHome, "lib")

    // Resolve LLVM libraries by full path where possible (prefer static .a to avoid -L on this
    // LLVM's lib/ shadowing the system libc++ — the prebuilt LLVM ships an ABI-incompatible
    // libc++.dylib). When only the .dylib/.so is present (Homebrew's LLVM doesn't ship .a),
    // fall back to the dynamic library.
    val llvmLibArgs = providers.exec {
        commandLine(llvmConfigBin.absolutePath, "--libs", "core", "support")
    }.standardOutput.asText.map { output ->
        output.trim().split("\\s+".toRegex())
            .filter { it.startsWith("-l") }
            .map { flag ->
                val name = flag.removePrefix("-l")
                val staticLib = File(llvmLibDir, "lib$name.a")
                if (staticLib.exists()) {
                    staticLib.absolutePath
                } else {
                    val dyn = listOf("dylib", "so").map { File(llvmLibDir, "lib$name.$it") }
                        .firstOrNull { it.exists() }
                    dyn?.absolutePath ?: error("No lib$name.{a,dylib,so} under ${llvmLibDir.absolutePath}")
                }
            }
    }

    val llvmSysLibs = providers.exec {
        commandLine(llvmConfigBin.absolutePath, "--system-libs")
    }.standardOutput.asText.map { output ->
        output.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    val compilerExec = if (llvmClangBin.exists()) llvmClangBin.absolutePath else "clang++"
    val flatbuffersHeadersAbs = flatbuffersHeadersDir.get().asFile.absolutePath
    val generatedCppAbs = generatedCppDir.get().asFile.absolutePath
    val outFileAbs = outFile.get().asFile.absolutePath
    val cppAbs = cpp.asFile.absolutePath
    val llvmIncludeAbs = File(llvmHome, "include").absolutePath
    val jniDirsAbs = jniIncludeDirs.map { it.absolutePath }
    val mac = isMac
    val libName = sharedLibName

    // Exec needs a valid commandLine at configuration time. Set a benign placeholder and
    // rebuild the real command in doFirst, where llvm-config providers can be resolved.
    commandLine("true")

    doFirst {
        val cmd = mutableListOf(
            compilerExec,
            "-std=c++17", "-fno-rtti", "-fPIC", "-shared", "-O0", "-g",
        )
        cmd += listOf("-I", llvmIncludeAbs)
        cmd += listOf("-I", flatbuffersHeadersAbs)
        cmd += listOf("-I", generatedCppAbs)
        for (dir in jniDirsAbs) {
            cmd += listOf("-I", dir)
        }
        if (mac) {
            cmd += listOf(
                "-undefined", "dynamic_lookup",
                "-Wl,-install_name,@rpath/$libName",
            )
        }
        cmd += "-o"
        cmd += outFileAbs
        cmd += cppAbs
        cmd += llvmLibArgs.get()
        cmd += llvmSysLibs.get()

        logger.lifecycle("Compiling native library: ${cmd.joinToString(" ")}")
        commandLine(cmd)
    }
}

// --- Kotlin/JVM side ---

dependencies {
    implementation(projects.rellBase.rrTree)
    implementation(projects.rellBase.rrSerialization)
    // Llvm_Backend implements Rt_Interpreter (runtime-core) and wraps Rt_InterpreterImpl
    // (runtime-interpreter) as its fallback for everything outside the JIT slice.
    implementation(projects.rellBase.runtimeCore)
    implementation(projects.rellBase.runtimeInterpreter)
    implementation(projects.rellBase.frontend)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(kotlin("test-junit5"))
    testImplementation(projects.rellBase)
    testImplementation(projects.rellBase.testUtils)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(buildNativeLibrary)
    systemProperty("rell.llvm.libpath", sharedLibFile.map { it.asFile.absolutePath }.get())
}
