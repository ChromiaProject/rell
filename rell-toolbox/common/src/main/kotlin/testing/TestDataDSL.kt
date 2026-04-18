/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.testing

import com.chromia.build.tools.keystore.ChromiaKeyStore
import com.chromia.cli.model.RellLibraryModel
import net.postchain.crypto.KeyPair
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class TestDataBuilder {

    private var content = """
        module;
        struct module_args { name = "Baloo"; }
        query hello() = "Hi!";
        operation call_op(value: integer) {}
    """.trimIndent()
    private val sourceFiles = mutableMapOf<String, () -> String>()
    private val workspaceFiles = mutableMapOf<String, () -> String>()
    private val createdSourceFiles = mutableMapOf<String, File>()
    private val configBuilder = ConfigBuilder()
    private var secretBuilder: SecretBuilder? = null
    private var keysStoreBuilder: KeyStoreBuilder? = null
    private var linterConfigBuilder: LinterConfigBuilder? = null
    private var formatterConfigBuilder: FormatterConfigBuilder? = null
    private var srcDir: File? = null
    private var wsDir: File? = null
    private var configFile: File? = null

    val sourceFolder: File
        get() = srcDir!!
    val sourceFolderUri: URI
        get() = sourceFolder.toURI()

    val workspaceFolder: File
        get() = wsDir!!
    val workspaceFolderUri: URI
        get() = workspaceFolder.toURI()

    val chromiaConfigFile: File
        get() = configFile!!
    val chromiaConfigFileUri: URI
        get() = chromiaConfigFile.toURI()
    val mainFile: File
        get() = sourceFile(MAIN_FILE_NAME)
    val mainFileUri: URI
        get() = mainFile.toURI()

    fun sourceFile(relativePath: String): File = createdSourceFiles[sourceFolder.resolve(relativePath).absolutePath]!!

    fun content(init: String) {
        content = init
    }

    fun emptyRellModule(name: String) {
        addFile(name, "module;")
    }

    fun addMainFile(content: String) {
        addFile(MAIN_FILE_NAME, content)
    }

    fun addFile(name: String, content: String) {
        sourceFiles[name] = { content }
    }

    fun addModule(relativePath: String, content: String = "module;") {
        sourceFiles["$relativePath/module.rell"] = { content }
    }

    fun appendToSourceFile(relativePath: String, content: String) {
        val sourceFile = sourceFile(relativePath)
        sourceFile.appendText(content, Charsets.UTF_8)
    }

    fun addWorkspaceFile(name: String, content: String) {
        workspaceFiles[name] = { content }
    }

    internal fun createFiles(target: Path) {
        wsDir = target.toFile()
        srcDir = File(wsDir, "src")
        srcDir?.mkdirs()
        sourceFiles.forEach { (name, content) ->
            createFileOnDisk(srcDir!!, name, content())
        }
        workspaceFiles.forEach { (name, content) ->
            createFileOnDisk(wsDir!!, name, content())
        }
        configFile = configBuilder.createFile(target)
        secretBuilder?.createFile(target)
        keysStoreBuilder?.createFiles(target)
        linterConfigBuilder?.createFile(target)
        formatterConfigBuilder?.createFile(target)
    }

    private fun createFileOnDisk(targetDir: File, relativePath: String, content: String): File {
        val file = File(targetDir, relativePath).apply {
            parentFile.mkdirs()
            writeText(content)
        }
        createdSourceFiles[file.absolutePath] = file
        return file
    }

    fun config(init: ConfigBuilder.() -> Unit) {
        init(configBuilder)
    }

    fun secret(init: SecretBuilder.() -> Unit = {}) {
        secretBuilder = SecretBuilder()
        init(secretBuilder!!)
    }

    fun keyStore(init: KeyStoreBuilder.() -> Unit = {}) {
        keysStoreBuilder = KeyStoreBuilder()
        init(keysStoreBuilder!!)
    }

    fun linter(init: LinterConfigBuilder.() -> Unit = {}) {
        val linterConfigBuilder = LinterConfigBuilder()
        init(linterConfigBuilder)
    }

    fun formatter(init: FormatterConfigBuilder.() -> Unit = {}) {
        val formatterConfigBuilder = FormatterConfigBuilder()
        init(formatterConfigBuilder)
    }

    fun createSourceFile(relativePath: String, content: String): File {
        addFile(relativePath, content)
        return createFileOnDisk(sourceFolder, relativePath, content)
    }

    fun createWorkspaceFile(relativePath: String, content: String): File {
        addWorkspaceFile(relativePath, content)
        return createFileOnDisk(workspaceFolder, relativePath, content)
    }

    companion object {
        val keyPair = KeyPair.of(
            "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
            "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"
        )
        private const val MAIN_FILE_NAME = "main.rell"
    }
}

class ConfigBuilder {
    private var definitions = ""
    private var content = """
        blockchains:
          hello:
            module: main
            config:
              blockstrategy:
                maxblocktime: 1000
    """.trimIndent()
    private var deployments = ""
    private val libStart = "\nlibs:\n"
    private var libs = libStart
    private var test = ""
    private var docs = ""
    private var compile = ""
    private var extra = ""
    private var database = """
        database:
          schema: integration_test_schema
    """.trimIndent()

    fun blockchains(init: String) {
        content = init
    }

    fun deployments(init: String) {
        deployments = init
    }

    // Intent is to use either of the lib function never both
    fun addLib(name: String, lib: RellLibraryModel) = apply {
        formatLibModule(name, lib)
    }

    // Intent is to use either of the lib function never both
    fun setFullLib(init: String) = apply {
        libs = init
    }

    private fun formatLibModule(name: String, lib: RellLibraryModel) {
        val sb = StringBuilder()
        sb.append(libs).append(lib.format(name))
        libs = sb.toString()
    }

    fun test(init: String) {
        test = init
    }

    fun docs(init: String) {
        docs = init
    }

    fun compile(init: String) {
        compile = init
    }

    fun extra(init: String) {
        extra = init
    }

    fun database(init: String) {
        database = init
    }

    fun definitions(init: String) {
        definitions = init
    }

    internal fun createFile(target: Path): File {
        val file = File(target.toFile(), "chromia.yml")
        file.bufferedWriter().use { writer ->
            if (definitions.isNotEmpty()) writer.append("$definitions\n")
            writer.append(content)
            if (deployments.isNotEmpty()) writer.append("\n$deployments")
            if (libs != libStart) writer.append("\n$libs")
            if (test.isNotEmpty()) writer.append("\n$test")
            if (docs.isNotEmpty()) writer.append("\n$docs")
            writer.append("\n$database")
            if (compile.isNotEmpty()) writer.append("\n$compile")
            if (extra.isNotEmpty()) writer.append("\n$extra")
        }
        return file
    }
}

class SecretBuilder {
    fun createFile(target: Path) {
        File(target.toFile(), ".chromia/config").also {
            it.parentFile.mkdirs()
        }.writeText(
            """
           pubkey=${TestDataBuilder.keyPair.pubKey.hex()}
           privkey=${TestDataBuilder.keyPair.privKey.hex()}
            """.trimIndent()
        )
    }
}

class KeyStoreBuilder {
    private val keyIdName: String = "keyIdUsedForTesting"
    fun createFiles(target: Path) {
        EnvironmentVariables("CHROMIA_HOME", target.absolutePathString()).execute {
            ChromiaKeyStore(keyIdName).saveKeyPair(TestDataBuilder.keyPair)
        }

        File(target.toFile(), "/config").also {
            it.parentFile.mkdirs()
        }.writeText(
            """
           key.id = $keyIdName
            """.trimIndent()
        )
    }
}

class LinterConfigBuilder {
    private var content = ""

    fun config(init: String) {
        content = init
    }

    fun createFile(target: Path) {
        File(target.toFile(), ".rell_lint").also {
            it.parentFile.mkdirs()
        }.writeText(content)
    }
}

class FormatterConfigBuilder {
    private var content = ""

    fun config(init: String) {
        content = init
    }

    fun createFile(target: Path) {
        File(target.toFile(), ".rell_format").also {
            it.parentFile.mkdirs()
        }.writeText(content)
    }
}

fun testData(dir: Path, init: TestDataBuilder.() -> Unit = {}): TestDataBuilder {
    val testDataBuilder = TestDataBuilder()
    testDataBuilder.apply(init)
        .createFiles(dir)
    return testDataBuilder
}

fun testData(dir: File, init: TestDataBuilder.() -> Unit = {}): TestDataBuilder {
    return testData(dir.toPath(), init)
}
