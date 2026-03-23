/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.extracting
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.startsWith
import com.chromia.cli.model.RellLibraryModel
import io.mockk.every
import io.mockk.mockk
import net.postchain.rell.toolbox.common.Position
import net.postchain.rell.toolbox.common.Range
import net.postchain.rell.toolbox.common.TextEdit
import net.postchain.rell.toolbox.formatter.DeltaType
import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.AbstractFormattingStyleLinter
import net.postchain.rell.toolbox.linter.AbstractRellLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.testing.testData
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
        testData(dir) {
            emptyRellModule("rell_file.rell")
            emptyRellModule("directory/rell_file.rell")
            addFile("not_a_rell_file.json", "{module}")
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
        val notAllowedFilePath = "not_allowed.rell"
        val allowedFilePath = "directory/rell_file.rell"
        val testDataBuilder = testData(dir) {
            emptyRellModule(allowedFilePath)
            emptyRellModule(notAllowedFilePath)
        }

        // Set not allowed permissions
        val allowedFile = testDataBuilder.sourceFile(allowedFilePath)
        val notAllowedFile = testDataBuilder.sourceFile(notAllowedFilePath)
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
    fun `initialFileIndexBuild builds index mapper of files in workspace imports file with error and same name`(
        @TempDir dir: File
    ) {
        testData(dir) {
            addFile(
                "directory/main.rell",
                """
                module;
                import ^^.main.*;

                function d() {
                    create f(name = "");
                }
                """.trimIndent()
            )
            addMainFile(
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
        testData(dir) {
            addMainFile(
                """
                module;
                import ^.importer.*;

                function d() {
                    create f(name = "");
                }
                """.trimIndent()
            )
            addFile(
                "importer.rell",
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

        every {
            formattingStyleLinter.enhanceWithFormatterIssues(linterOptions, formatterOptions, any(), any())
        } answers {
            val resource = thirdArg<Resource>()
            resource.formatterIssues = listOf(
                FormatterIssue("foo", DeltaType.CHANGE, 2, 2, TextEdit(Range(Position(1, 1), Position(2, 2)), ""))
            )
        }
        every { rellLinter.enhanceWithLintIssues(linterOptions, any()) } answers {
            val resource = secondArg<Resource>()
            val context = ParserRuleContext()
            context.start = CommonToken(Pair(null, null), 1, 1, 1, 1)
            resource.linterIssues = listOf(
                TestLinterIssue(context, "", "")
            )
        }

        val fileContent = """
            module;

            function notSnakeCase() {
            	return 2;
            }

            function foo() = unknown();

        """.trimIndent()
        val externalLibName = "external"
        val internalLibFile = "lib/internal/module.rell"
        val externalLibFile = "lib/$externalLibName/module.rell"

        val testDataBuilder = testData(dir) {
            addMainFile(fileContent)
            addFile(internalLibFile, fileContent)
            addFile(externalLibFile, fileContent)
            config {
                blockchains(
                    """
                    blockchains:
                        rellDappWithLib:
                            module: main
                    """.trimIndent()
                )
                addLib(externalLibName, RellLibraryModel("a.registry.abc", null, "a/path/to/registry", false, null))
            }
        }

        val workspaceIndexer =
            WorkspaceIndexer(
                testDataBuilder.sourceFolderUri,
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions,
                dir.toURI()
            )
        workspaceIndexer.initialFileIndexBuild()

        val allIssues = workspaceIndexer.getAllIssues()
        val mainFileUri = testDataBuilder.mainFileUri
        val internalLibFileUri = testDataBuilder.sourceFile(internalLibFile).toURI()
        val externalLibFileUri = testDataBuilder.sourceFile(externalLibFile).toURI()

        assertThat(allIssues.keys).containsOnly(mainFileUri, internalLibFileUri, externalLibFileUri)
        assertThat(allIssues[mainFileUri]!!.size).isEqualTo(3)
        assertThat(allIssues[internalLibFileUri]!!.size).isEqualTo(3)
        assertThat(allIssues[externalLibFileUri]!!.size).isEqualTo(1)
        assertThat(allIssues[externalLibFileUri]!!.first().code).isEqualTo("unknown_name:unknown")
    }

    @Test
    fun `getAllLintAndFormatIssues returns all expected issues`(@TempDir dir: File) {
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
        val testDataBuilder = testData(dir) {
            addMainFile(fileContent)
        }

        val linterOptions = LinterOptions(enabled = true, ruleNamingConvention = true, ruleFormatter = true)
        val formatterOptions = FormatterOptions(tabSize = 0)

        val workspaceIndexer =
            WorkspaceIndexer(
                testDataBuilder.sourceFolderUri,
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions,
                dir.toURI()
            )
        workspaceIndexer.initialFileIndexBuild()
        val lintFormatIssues = workspaceIndexer.getAllLintAndFormatIssues()

        val mainFileUri = testDataBuilder.mainFileUri
        assertThat(lintFormatIssues.keys).containsOnly(mainFileUri)
        lintFormatIssues[mainFileUri]!!.forEach {
            assertThat(it.code).startsWith("linter")
        }
    }

    @Test
    fun `Rell Version default gives smart null check error`(@TempDir dir: File) {
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

        val testDataBuilder = testData(dir) {
            addMainFile(fileContent)
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
        val mainFileUri = testDataBuilder.mainFileUri
        assertThat(allIssues.keys).containsOnly(mainFileUri)
        assertThat(allIssues[mainFileUri]!!).extracting { it.code to it.message }.containsExactlyInAnyOrder(
            "expr:smartnull:var:never:[abc]" to "Variable 'abc' cannot be null at this location",
        )
    }

    @Test
    fun `Rell Version 0-14-1 gives smart null check error`(@TempDir dir: File) {
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

        val testDataBuilder = testData(dir) {
            addMainFile(fileContent)
            config {
                blockchains(
                    """
                blockchains:
                  rellDappWithLib:
                    module: main
                    """.trimIndent()
                )

                compile(
                    """
                compile:
                  rellVersion: 0.14.1
                    """.trimIndent()
                )
            }
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
        val mainFileUri = testDataBuilder.mainFileUri
        assertThat(allIssues.keys).containsOnly(mainFileUri)
        assertThat(allIssues[mainFileUri]!!.size).isEqualTo(1)
        assertThat(allIssues[mainFileUri]!!.first().code).isEqualTo("expr:smartnull:var:never:[abc]")
    }
}
