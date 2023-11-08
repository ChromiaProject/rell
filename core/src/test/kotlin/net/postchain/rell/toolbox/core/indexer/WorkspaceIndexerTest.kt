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
    fun `initialFileIndexBuild builds index mapper of files in workspace imports file with error`(@TempDir dir: File) {
        File(dir, "main.rell").apply {
            writeText("""
                module;
                import ^.importer.*;
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

        assertThat(workspaceIndexer.fileUriResourceMap.size).isEqualTo(2)
        workspaceIndexer.fileUriResourceMap.forEach {
            assertThat(it.value.parseTree.children).isNotEmpty()
            assertThat(it.value.semanticErrors[0].code == "unknown_name:b")
        }
    }


    @Test
    fun `initialFileIndexBuild builds index mapper of files in workspace with errors`(@TempDir dir: File) {
        File(dir, "rell_syntax_error.rell").apply {
            writeText("""
                module;
                function a() {
                    val a = 2
                }
            """.trimIndent())
        }
        File(dir, "rell_semantic_error.rell").apply {
            writeText("""
                module;
                function a() {
                    create b(name = "");
                }
            """.trimIndent())
        }

        File(dir, "rell_semantic_and_syntax_error.rell").apply {
            writeText("""
                module;
                
                function a() {
                    create b(name = "");
                }
                val v = ;
            """.trimIndent())
        }

        File(dir, "rell_semantic_and_syntax_error_other_order.rell").apply {
            writeText("""
                module;
                function a()() {
                    create b(name = "");
                }
                function c() {
                    create b(name ="");
                }
            """.trimIndent())
        }
        val workspaceIndexer = WorkspaceIndexer(dir.toURI())
        workspaceIndexer.initialFileIndexBuild()
        //Manual testing for now, will be improved
        workspaceIndexer.fileUriResourceMap.forEach {
            println("-----------------------")
            println(it.key)
            println(it.value.semanticErrors)
            println(it.value.syntaxErrors)
            println("-----------------------")
        }
    }
}
