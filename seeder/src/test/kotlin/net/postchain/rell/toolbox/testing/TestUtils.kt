package net.postchain.rell.toolbox.testing

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Creates a temporary file with the given content for testing purposes.
 */
fun testData(filename: String, content: String): Path {
    val tempDir = Files.createTempDirectory("test-data")
    val filePath = tempDir.resolve(filename)
    filePath.writeText(content)
    return filePath
}

/**
 * Creates a temporary directory structure for testing purposes.
 * Example:
 * ```
 * val testDir = testDirectory {
 *     file("test.txt", "content")
 *     directory("subdir") {
 *         file("nested.txt", "nested content")
 *     }
 * }
 * ```
 */
fun testDirectory(block: TestDirectoryBuilder.() -> Unit): Path {
    val tempDir = Files.createTempDirectory("test-directory")
    val builder = TestDirectoryBuilder(tempDir)
    builder.block()
    return tempDir
}

class TestDirectoryBuilder(private val basePath: Path) {
    fun file(name: String, content: String): Path {
        val filePath = basePath.resolve(name)
        Files.createDirectories(filePath.parent)
        filePath.writeText(content)
        return filePath
    }
    
    fun directory(name: String, block: TestDirectoryBuilder.() -> Unit): Path {
        val dirPath = basePath.resolve(name)
        Files.createDirectories(dirPath)
        val builder = TestDirectoryBuilder(dirPath)
        builder.block()
        return dirPath
    }
}