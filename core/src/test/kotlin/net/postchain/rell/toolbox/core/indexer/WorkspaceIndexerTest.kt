package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createDirectory

class WorkspaceIndexerTest {
    @Test
    fun `initialFileIndexBuild builds index mapper of files in workspace`(@TempDir dir: File) {
        val childDir = File(dir, "directory").toPath().createDirectory()
        File(childDir.toFile(), "rell_file.rell").apply {
            writeText("module;")
        }
        File(dir, "rell_file.rell").apply {
            writeText("module;")
        }
        File(dir, "not_a_rell_file.json").apply {
            writeText("{module}")
        }

        val workspaceIndexer = WorkspaceIndexer(dir.toURI())
        workspaceIndexer.initialFileIndexBuild()

        assertThat(workspaceIndexer.fileUriResourceMap.size).isEqualTo(2)
        workspaceIndexer.fileUriResourceMap.forEach {
            assertThat(it.value.parseTree.children).isNotEmpty()
        }
    }

    @Test
    fun `initialFileIndexBuild builds index mapper of files in workspace imports file with error and same name`(@TempDir dir: File) {
        val childDir = File(dir, "directory").toPath().createDirectory()
        File(childDir.toFile(), "main.rell").apply {
            writeText("""
                module;
                import ^^.main.*;
                
                function d() {
                    create f(name = "");
                }
            """.trimIndent())
        }
        File(dir, "main.rell").apply {
            writeText("""
                module;
                function a() {
                    create b(name = "");
                }
            """.trimIndent())
        }

        val workspaceIndexer = WorkspaceIndexer(dir.toURI())
        workspaceIndexer.initialFileIndexBuild()

        assertThat(workspaceIndexer.fileUriResourceMap.size).isEqualTo(2)
        workspaceIndexer.fileUriResourceMap.forEach {
            assertThat(it.value.parseTree.children).isNotEmpty()
            assertThat(it.value.fileSpecificSemanticErrors.size).isEqualTo(1)
        }
    }

    @Test
    fun `initialFileIndexBuild builds index mapper of files in workspace imports file with error`(@TempDir dir: File) {

        File(dir, "main.rell").apply {
            writeText("""
                module;
                import ^.importer.*;
                
                function d() {
                    create f(name = "");
                }
            """.trimIndent())
        }
        File(dir, "importer.rell").apply {
            writeText("""
                module;
                function a() {
                    create b(name = "");
                }
            """.trimIndent())
        }

        val workspaceIndexer = WorkspaceIndexer(dir.toURI())
        workspaceIndexer.initialFileIndexBuild()

        workspaceIndexer.fileUriResourceMap.forEach {
            assertThat(it.value.parseTree.children).isNotEmpty()
            assertThat(it.value.fileSpecificSemanticErrors.size).isEqualTo(1)
        }
    }

    @Test
    fun `initialFileIndexBuild builds real world examples without crashing`() {
        val classLoader = javaClass.getClassLoader()
        val realWorldExamples = File(classLoader.getResource("realWorldExamples")!!.file).absoluteFile

        realWorldExamples.listFiles()!!.forEach { dir ->
            val srcDir = dir.resolve("rell/src")
            val workspaceIndexer = WorkspaceIndexer(srcDir.toURI())
            workspaceIndexer.initialFileIndexBuild()
            assertThat(workspaceIndexer.fileUriResourceMap).isNotEmpty()
        }
    }
}
