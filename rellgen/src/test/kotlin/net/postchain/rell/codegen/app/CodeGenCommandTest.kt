package net.postchain.rell.codegen.app

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

internal class CodeGenCommandTest {

    @Test
    fun test() {
        val dir = createTempDirectory("test")
        println(dir.pathString)
        val dir2 = File(dir.toFile(), "test")
        dir2.mkdirs()
        val moduleFile = File(dir2, "module.rell")
        println(moduleFile.absolutePath)
        val content = File(javaClass.getResource("module.rell")!!.toURI()).readText()
        moduleFile.createNewFile()
        moduleFile.writeText(content)
        assertThat(dir.toFile().listFiles()).hasSize(1)
        CodeGenCommand().parse(listOf(dir.pathString, "${dir.pathString}/target", "--module", "test", "--package", "com.example"))
        assertThat(dir.toFile().listFiles()).hasSize(2)
        assertThat(File(dir.toFile(), "target/test").list()).containsAll(
            "test.kt",
        )
    }
}