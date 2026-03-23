/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.caching

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.RellResourceFactory
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.lsp.server.VersionInfo
import net.postchain.rell.toolbox.parser.AntlrRellParser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class RellIndexSerializerTest {

    private lateinit var rellIndexSerializer: RellIndexSerializer
    private lateinit var mockRellLinter: RellLinter
    private lateinit var mockFormattingStyleLinter: FormattingStyleLinter
    private lateinit var mockFormatterOptionsResolver: RellFormatterOptionsResolver
    private lateinit var mockLinterOptionsResolver: RellLinterOptionsResolver
    private lateinit var workspaceFolderUri: URI

    private val linterOptions = LinterOptions()
    private val formatterOptions = FormatterOptions()

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        mockRellLinter = mockk()
        mockFormattingStyleLinter = mockk()
        mockFormatterOptionsResolver = mockk()
        mockLinterOptionsResolver = mockk()
        workspaceFolderUri = Files.createDirectories(tempDir.resolve("src")).toUri()

        every { mockLinterOptionsResolver.getLinterConfig(workspaceFolderUri) } returns linterOptions
        every {
            mockFormatterOptionsResolver.getWorkspaceFormattingOptions(workspaceFolderUri)
        } returns formatterOptions

        rellIndexSerializer = RellIndexSerializer(
            mockRellLinter,
            mockFormattingStyleLinter,
            mockFormatterOptionsResolver,
            mockLinterOptionsResolver
        )
    }

    @Test
    fun `serializeAsBytes should serialize WorkspaceIndexer correctly`() {
        val workspaceIndexer = createTestWorkspaceIndexer()

        val serializedBytes = rellIndexSerializer.serializeAsBytes(workspaceIndexer)

        assertThat(serializedBytes).isNotNull()
        assertThat(serializedBytes).isNotEmpty()
    }

    @Test
    fun `deserializeAsWorkspaceIndexer should deserialize bytes correctly`() {
        val originalIndexer = createTestWorkspaceIndexer()

        val serializedBytes = rellIndexSerializer.serializeAsBytes(originalIndexer)
        val deserializedIndexer = rellIndexSerializer.deserializeAsWorkspaceIndexer(serializedBytes)

        assertThat(deserializedIndexer).isNotNull()
        assertThat(deserializedIndexer.workspaceUri).isEqualTo(originalIndexer.workspaceUri)
        assertThat(deserializedIndexer.fileUriResourceMap).hasSize(originalIndexer.fileUriResourceMap.size)
    }

    @Test
    fun `serialization and deserialization should maintain data integrity`() {
        val originalIndexer = createTestWorkspaceIndexer()

        val serializedBytes = rellIndexSerializer.serializeAsBytes(originalIndexer)
        val deserializedIndexer = rellIndexSerializer.deserializeAsWorkspaceIndexer(serializedBytes)

        assertThat(deserializedIndexer.workspaceUri).isEqualTo(originalIndexer.workspaceUri)
        assertThat(deserializedIndexer.projectRootUri).isEqualTo(originalIndexer.projectRootUri)
        assertThat(deserializedIndexer.fileUriResourceMap.keys).isEqualTo(originalIndexer.fileUriResourceMap.keys)
    }

    @Test
    fun `should handle empty WorkspaceIndexer`() {
        val emptyIndexer = WorkspaceIndexer(
            workspaceFolderUri,
            mockRellLinter,
            LinterOptions(),
            mockFormattingStyleLinter,
            FormatterOptions()
        )

        val serializedBytes = rellIndexSerializer.serializeAsBytes(emptyIndexer)
        val deserializedIndexer = rellIndexSerializer.deserializeAsWorkspaceIndexer(serializedBytes)

        assertThat(deserializedIndexer).isNotNull()
        assertThat(deserializedIndexer.workspaceUri).isEqualTo(emptyIndexer.workspaceUri)
        assertThat(deserializedIndexer.fileUriResourceMap).hasSize(0)
    }

    @Test
    fun `should throw exception when version mismatch occurs`() {
        val indexer = createTestWorkspaceIndexer()

        mockkObject(VersionInfo)
        every { VersionInfo.getImplementationVersion() } returns "1.0.0"

        val serializedBytes = rellIndexSerializer.serializeAsBytes(indexer)

        every { VersionInfo.getImplementationVersion() } returns "2.0.0"

        val exception = assertThrows<IllegalStateException> {
            rellIndexSerializer.deserializeAsWorkspaceIndexer(serializedBytes)
        }

        assertThat(exception.message).isNotNull()
        assertThat(exception.message!!).contains("different version")
        assertThat(exception.message!!).contains("Expected: 2.0.0")
        assertThat(exception.message!!).contains("Found: 1.0.0")

        unmockkObject(VersionInfo)
    }

    @Test
    fun `should handle null metadata gracefully`() {
        val serializableIndexer = SerializableWorkspaceIndexer(
            workspaceUri = workspaceFolderUri,
            serializableResources = emptyList(),
            linterOptions = linterOptions,
            formatterOptions = formatterOptions,
            projectRootUri = null,
            metaData = null
        )

        val serializedBytes = RellIndexSerializer.getFury().serialize(serializableIndexer)

        val exception = assertThrows<IllegalStateException> {
            rellIndexSerializer.deserializeAsWorkspaceIndexer(serializedBytes)
        }

        assertThat(exception.message).isNotNull()
        assertThat(exception.message!!).contains("different version")
        assertThat(exception.message!!).contains("Found: null")
    }

    private fun createTestWorkspaceIndexer(): WorkspaceIndexer {
        val resourceFactory = RellResourceFactory(workspaceFolderUri, AntlrRellParser(), ChromiaModelProvider(null))
        val indexer = WorkspaceIndexer(
            workspaceFolderUri,
            mockRellLinter,
            LinterOptions(),
            mockFormattingStyleLinter,
            FormatterOptions()
        )

        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val testFile = File(workspaceFolderUri.resolve("test.rell"))
        testFile.writeText("module; function test() {}")

        indexer.fileUriResourceMap[testFile.toURI()] = resourceFactory.buildRellResource(testFile.toURI(), fileMap)

        return indexer
    }
}
