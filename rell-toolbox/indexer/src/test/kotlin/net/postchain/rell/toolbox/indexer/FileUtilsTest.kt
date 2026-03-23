/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class FileUtilsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test findRellFilesInWorkspace with no rell files`() {
        val uris = mutableListOf<URI>()
        val emptyDir = File(tempDir.toUri())

        findRellFilesInWorkspace(emptyDir, uris)
        assertThat(uris).hasSize(0)
    }

    @Test
    fun `test findRellFilesInWorkspace with rell files`() {
        val uris = mutableListOf<URI>()

        val testDir = File(tempDir.toUri())
        val file1 = File(testDir, "test1.rell")
        file1.createNewFile()
        val nonRellFile = File(testDir, "test.txt")
        nonRellFile.createNewFile()

        val subDir = File(testDir, "subdir")
        subDir.mkdir()
        val file2 = File(subDir, "test2.rell")
        file2.createNewFile()

        findRellFilesInWorkspace(testDir, uris)

        assertThat(uris).hasSize(2)
        assertThat(uris.map { it.path }).contains(file1.toURI().path)
        assertThat(uris.map { it.path }).contains(file2.toURI().path)
        assertThat(uris.all { uri -> uri.toString().startsWith("file:/") }).isTrue()
    }

    @Test
    fun `test findRellFilesInWorkspace with nested directories`() {
        val uris = mutableListOf<URI>()

        val testDir = File(tempDir.toUri())
        val level1 = File(testDir, "level1")
        level1.mkdir()
        val level2 = File(level1, "level2")
        level2.mkdir()
        val level3 = File(level2, "level3")
        level3.mkdir()

        val file1 = File(level1, "test1.rell")
        file1.createNewFile()
        val file2 = File(level2, "test2.rell")
        file2.createNewFile()
        val file3 = File(level3, "test3.rell")
        file3.createNewFile()

        findRellFilesInWorkspace(testDir, uris)

        assertThat(uris).hasSize(3)
        assertThat(uris.map { it.path }).contains(file1.toURI().path)
        assertThat(uris.map { it.path }).contains(file2.toURI().path)
        assertThat(uris.map { it.path }).contains(file3.toURI().path)

        assertThat(uris.all { uri -> uri.toString().startsWith("file:/") }).isTrue()
    }

    @Test
    fun `test sha256 with string input`() {
        val input = "test string"
        val result = sha256(input)
        assertThat(result).isNotEmpty()
        assertThat(result.length).isEqualTo(64) // SHA-256 produces 64 hex characters
    }

    @Test
    fun `test sha256 with byte array input`() {
        val input = "test bytes".toByteArray()
        val expected = "4be66ea6f5222861df37e88d4635bffb99e183435f79fba13055b835b5dc420b"
        val result = sha256(input)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test sha256 with empty string`() {
        val input = ""
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val result = sha256(input)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test sha256 with empty byte array`() {
        val input = ByteArray(0)
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val result = sha256(input)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test calculateChecksum with file input`() {
        val testFile = Files.createFile(tempDir.resolve("checksum-test.txt"))
        Files.writeString(testFile, "test content")

        val result = calculateChecksum(testFile.toFile())
        assertThat(result).isEqualTo("6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72")
    }

    @Test
    fun `test calculateChecksum with URI input`() {
        val testFile = Files.createFile(tempDir.resolve("checksum-test-uri.txt"))
        Files.writeString(testFile, "test content")

        val result = calculateChecksum(testFile.toUri())
        assertThat(result).isEqualTo("6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72")
    }

    @Test
    fun `test calculateChecksum with string input`() {
        val input = "test content"
        val result = calculateChecksum(input)
        assertThat(result).isEqualTo("6ae8a75555209fd6c44157c0aed8016e763ff435a19cf186f76863140143ff72")
    }

    @Test
    fun `test calculateChecksum with empty file`() {
        val emptyFile = Files.createFile(tempDir.resolve("empty-file.txt"))
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val result = calculateChecksum(emptyFile.toFile())
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test calculateChecksum with empty string`() {
        val input = ""
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val result = calculateChecksum(input)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test checksums are consistent`() {
        val content = "test consistency"
        val testFile = Files.createFile(tempDir.resolve("consistency-test.txt"))
        Files.writeString(testFile, content)

        val checksumFromString = calculateChecksum(content)
        val checksumFromFile = calculateChecksum(testFile.toFile())
        val checksumFromUri = calculateChecksum(testFile.toUri())

        assertThat(checksumFromString).isEqualTo(checksumFromFile)
        assertThat(checksumFromFile).isEqualTo(checksumFromUri)
    }
}
