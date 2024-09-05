package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.startsWith
import io.mockk.every
import io.mockk.mockk
import net.postchain.rell.toolbox.core.Position
import net.postchain.rell.toolbox.core.Range
import net.postchain.rell.toolbox.core.TextEdit
import net.postchain.rell.toolbox.formatter.DeltaType
import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.AbstractFormattingStyleLinter
import net.postchain.rell.toolbox.linter.AbstractRellLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Pair
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectory


class WorkspaceIndexerTest {
    private val rellLinter = mockk<AbstractRellLinter>()
    private val formattingStyleLinter = mockk<AbstractFormattingStyleLinter>()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()

    @BeforeEach
    fun setup() {
        every { rellLinter.enhanceWithLintIssues(any(), any()) } returns Unit
        every { formattingStyleLinter.enhanceWithFormatterIssues(any(), any(), any(), any()) } returns Unit
    }

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

        val workspaceIndexer =
            WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        workspaceIndexer.initialFileIndexBuild()

        assertThat(workspaceIndexer.fileUriResourceMap.size).isEqualTo(2)
        workspaceIndexer.fileUriResourceMap.forEach {
            assertThat(it.value.parseTree.children).isNotEmpty()
        }
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `initialFileIndexBuild skips files not able to read`(@TempDir dir: File) {
        val childDir = File(dir, "directory").toPath().createDirectory()
        val allowedFile = File(childDir.toFile(), "rell_file.rell").apply {
            writeText("module;")
        }
        val notAllowedFile = File(dir, "not_allowed.rell").apply {
            writeText("module;")
        }
        val notAllowedPermissions = PosixFilePermissions.fromString("---------")
        Files.setPosixFilePermissions(notAllowedFile.toPath(), notAllowedPermissions)

        val workspaceIndexer =
            WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        workspaceIndexer.initialFileIndexBuild()

        assertThat(workspaceIndexer.fileUriResourceMap.keys().toList()).containsOnly(allowedFile.toURI())

        // Restore permissions
        val allowedPermissions = PosixFilePermissions.fromString("rwxr-x---")
        Files.setPosixFilePermissions(notAllowedFile.toPath(), allowedPermissions)
    }

    @Test
    fun `initialFileIndexBuild builds index mapper of files in workspace imports file with error and same name`(@TempDir dir: File) {
        val childDir = File(dir, "directory").toPath().createDirectory()
        File(childDir.toFile(), "main.rell").apply {
            writeText(
                """
                module;
                import ^^.main.*;
                
                function d() {
                    create f(name = "");
                }
            """.trimIndent()
            )
        }
        File(dir, "main.rell").apply {
            writeText(
                """
                module;
                function a() {
                    create b(name = "");
                }
            """.trimIndent()
            )
        }

        val workspaceIndexer =
            WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
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
            writeText(
                """
                module;
                import ^.importer.*;
                
                function d() {
                    create f(name = "");
                }
            """.trimIndent()
            )
        }
        File(dir, "importer.rell").apply {
            writeText(
                """
                module;
                function a() {
                    create b(name = "");
                }
            """.trimIndent()
            )
        }

        val workspaceIndexer =
            WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
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
            val workspaceIndexer =
                WorkspaceIndexer(srcDir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
            workspaceIndexer.initialFileIndexBuild()
            assertThat(workspaceIndexer.fileUriResourceMap).isNotEmpty()
        }
    }

    @Test
    fun `Linter issues from external libs is not reported`(@TempDir dir: File) {
        val linterOptions = LinterOptions(enabled = true, ruleNamingConvention = true, ruleFormatter = true)
        val formatterOptions = FormatterOptions(tabSize = 0)

        every { formattingStyleLinter.enhanceWithFormatterIssues(linterOptions, formatterOptions, any(), any()) } answers {
            val resource = thirdArg<Resource>()
            resource.formatterIssues = listOf(
                FormatterIssue("foo", DeltaType.CHANGE, 2, 2, TextEdit(Range(Position(1,1), Position(2,2)), ""))
            )
        }
        every { rellLinter.enhanceWithLintIssues(linterOptions, any()) } answers {
            val resource = secondArg<Resource>()
            val context = ParserRuleContext()
            context.start = CommonToken(Pair(null, null), 1, 1,1, 1)
            resource.linterIssues = listOf(
                TestLinterIssue(context, "", "")
            )
        }

        val srcDir = File(dir, "rellDapp/src")
        srcDir.mkdirs()

        val externalLibName = "external"
        val fileContent = """
            module;

            function notSnakeCase() {
            	return 2;
            }

            function foo() = unknown();

        """.trimIndent()

        val main = File(srcDir, "main.rell").apply {
            parentFile.mkdirs()
            writeText(fileContent)
        }.toURI()

        val internalLib = File(srcDir, "lib/internal/module.rell").apply {
            parentFile.mkdirs()
            writeText(fileContent)
        }.toURI()

        val externalLib = File(srcDir, "lib/$externalLibName/module.rell").apply {
            parentFile.mkdirs()
            writeText(fileContent)
        }.toURI()

        File(dir, "chromia.yml").apply {
            writeText(
                """
                blockchains:
                  rellDappWithLib:
                    module: main

                libs:
                  $externalLibName:
                    registry: a.registry.abc
                    path: a/path/to/registry
            """.trimIndent()
            )
        }

        val workspaceIndexer =
            WorkspaceIndexer(
                srcDir.toURI(),
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions,
                dir.toURI()
            )
        workspaceIndexer.initialFileIndexBuild()
        val allIssues = workspaceIndexer.getAllIssues()

        assertThat(allIssues.keys).containsOnly(main, internalLib, externalLib)
        assertThat(allIssues[main]!!.size).isEqualTo(3)
        assertThat(allIssues[internalLib]!!.size).isEqualTo(3)
        assertThat(allIssues[externalLib]!!.size).isEqualTo(1)
        assertThat(allIssues[externalLib]!!.first().code).isEqualTo("unknown_name:unknown")
    }

    @Test
    fun `getAllLintAndFormatIssues returns all expected issues`(@TempDir dir: File) {
        val srcDir = File(dir, "rellDapp/src")
        srcDir.mkdirs()

        val fileContent = """
            module;
            /**
            * @return faulty-tag
            */
            function notSnakeCase() {
            	return 2
            }

            function foo()  =  unknown();

        """.trimIndent()

        val mainUri = File(srcDir, "main.rell").apply {
            parentFile.mkdirs()
            writeText(fileContent)
        }.toURI()


        val linterOptions = LinterOptions(enabled = true, ruleNamingConvention = true, ruleFormatter = true)
        val formatterOptions = FormatterOptions(tabSize = 0)

        val workspaceIndexer =
            WorkspaceIndexer(
                srcDir.toURI(),
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions,
                dir.toURI()
            )
        workspaceIndexer.initialFileIndexBuild()
        val lintFormatIssues = workspaceIndexer.getAllLintAndFormatIssues()

        assertThat(lintFormatIssues.keys).containsOnly(mainUri)
        lintFormatIssues[mainUri]!!.forEach {
            assertThat(it.code).startsWith("linter")
        }
    }

    @Test
    fun `Rell Version default gives no smart null check error`(@TempDir dir: File) {
        val srcDir = File(dir, "rellDapp/src")
        srcDir.mkdirs()

        val fileContent = """
         module;
            
         entity foo {
            name;
            bool: boolean;
         }
                
         function bar() {
            val abc = foo @? { .name == "hello" };
            require(exists(abc), "abc is real");
            require(abc?.bool, "b");
         }
        """.trimIndent()

        val main = File(srcDir, "main.rell").apply {
            parentFile.mkdirs()
            writeText(fileContent)
        }.toURI()

        val workspaceIndexer =
            WorkspaceIndexer(
                dir.toURI(),
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions,
                dir.toURI()
            )
        workspaceIndexer.initialFileIndexBuild()

        val allIssues = workspaceIndexer.getAllIssues()
        assertThat(allIssues.keys).containsOnly(main)
        assertThat(allIssues[main]!!.size).isEqualTo(0)
    }

    @Test
    fun `Rell Version 0-14-1 gives smart null check error`(@TempDir dir: File) {
        val srcDir = File(dir, "rellDapp/src")
        srcDir.mkdirs()

        val fileContent = """
         module;
            
         entity foo {
            name;
            bool: boolean;
         }
                
         function bar() {
            val abc = foo @? { .name == "hello" };
            require(exists(abc), "abc is real");
            require(abc?.bool, "b");
         }
        """.trimIndent()

        val main = File(srcDir, "main.rell").apply {
            parentFile.mkdirs()
            writeText(fileContent)
        }.toURI()

        File(dir, "chromia.yml").apply {
            writeText(
                """
                blockchains:
                  rellDappWithLib:
                    module: main
                
                compile:
                  rellVersion: 0.14.1
            """.trimIndent()
            )
        }


        val workspaceIndexer =
            WorkspaceIndexer(
                dir.toURI(),
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions,
                dir.toURI()
            )
        workspaceIndexer.initialFileIndexBuild()

        val allIssues = workspaceIndexer.getAllIssues()
        assertThat(allIssues.keys).containsOnly(main)
        assertThat(allIssues[main]!!.size).isEqualTo(1)
        assertThat(allIssues[main]!!.first().code).isEqualTo("expr:smartnull:var:never:[abc]")
    }
}
