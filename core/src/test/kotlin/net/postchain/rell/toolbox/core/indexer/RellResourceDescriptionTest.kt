package net.postchain.rell.toolbox.core.indexer

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createParentDirectories

class RellResourceDescriptionTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        val childDir = File(tempDir.toFile(), "directory/src").toPath().createParentDirectories().createDirectory()
        File(childDir.toFile(), "rell-file.rell").apply {
            writeText("module; import ^.moduleName.*;")
        }
        File(tempDir.toFile(), "rell-file.rell").apply {
            writeText("module;")
        }
        File(tempDir.toFile(), "not-a-rell-file.json").apply {
            writeText("{module}")
        }
    }

    @Test
    fun `just a runner`() {
        val rellDesc = RellResourceDescription()
        rellDesc.buildRellResource(File("/Users/tim/chromaway/test/oct").toURI())

        val a = rellDesc.fileUriModuleInfoMap
        val b = 2
    }
}