package net.postchain.rell.toolbox.lsp.completion

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.containsNone
import assertk.assertions.doesNotContain
import assertk.assertions.extracting
import net.postchain.rell.toolbox.common.RellKeywords
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

class RellCompletionServiceTest {

    @TempDir
    private lateinit var tempDir: File
    private lateinit var testDataBuilder: TestDataBuilder
    private lateinit var indexer: WorkspaceIndexer
    private val completionService = RellCompletionService(RellSymbolService())
    private val importerFilePath = "importer.rell"
    private val libraryFilePath = "library.rell"
    private val explicitImportFilePath = "explicit_token_importer.rell"
    private val fileInSameModule = "a/b/file1.rell"
    private val testFile1 = "test/test1.rell"
    private val testFile2 = "test/test2.rell"
    private val testFile3 = "test/test3.rell"

    @BeforeEach
    fun setup() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                query q() = 123;
                query p(x: integer) { val local_var = 4; return 'Hello ' + local_var; }
                entity person {
                    first: text;
                    last: text;
                }
                operation op() { create person( first='John', last='Doe' ); }
                namespace ns1 {
                    namespace ns2 {
                        query q2() = 123;
                    }
                    query q3() = 123;
                }
                query q4() {
                    return person@* {  };
                }
                query q5() {
                    return person@* { . };
                }
                """.trimIndent()
            )
            addFile(libraryFilePath, "module; function r(i: integer) = 123;")
            addFile(importerFilePath, "module; import library.*;")
            addModule(
                "a/b",
                """
                module;
                function fun_in_module() = 123;
                function another_fun() = 42;
                struct userStruct { name; }
                object userObject {}
                val CONSTANT = 11;
                operation operation1() {}
                query query1() {
                    return 1;
                }
                """.trimIndent()
            )
            addModule(
                "c/d",
                """
                module;
                import ^^.a.b.{};
                """.trimIndent()
            )
            addFile(
                fileInSameModule,
                """
                import
                function file1Function(i: integer) = 123;
                entity file1Entity {
                    name;
                }
                operation file1Operation () {}
                """.trimIndent()
            )
            addFile(
                explicitImportFilePath,
                """
                module;
                import a.b.{  };
                """.trimIndent()
            )
            addFile(
                testFile1,
                """
               @test module; 
               val PI = 3.14;
               val MAGIC_NUMBER = 1;
                """.trimIndent()
            )
            addFile(
                testFile2,
                """
                @test module;
                import test.test1.{  };
                """.trimIndent()
            )
            addFile(
                testFile3,
                """
                @test module;
                impor
                """.trimIndent()
            )
        }

        val rellLinter = RellLinter()
        val formattingStyleLinter = FormattingStyleLinter()
        val formatterOptions = FormatterOptions()
        val linterOptions = LinterOptions()
        indexer = WorkspaceIndexer(
            testDataBuilder.sourceFolderUri,
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions,
            testDataBuilder.workspaceFolderUri
        )
        indexer.initialFileIndexBuild()
    }

    @Test
    fun `Library functions suggested`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val offset = BEGINNING_OF_FILE_OFFSET
        val completions = completionService.getCompletions(mainFileUri, offset, indexer, mainFileUri.toDocument())
        val expectedLibraryFunctions = arrayOf(
            "abs" to "function",
            "min" to "function",
            "max" to "function",
            "print" to "function",
            "log" to "function",
            "require" to "function",
            "require_not_empty" to "function",
            "verify_signature" to "function",
            "sha256" to "function",
            "try_call" to "function",
            "keccak256" to "function",
        )

        assertThat(completions).extracting {
            it.label to it.labelDetails.description
        }.containsAll(*expectedLibraryFunctions)
    }

    data class CompletionItemData(
        val label: String?,
        val description: String?,
        val detail: String?,
        val kind: CompletionItemKind?,
        val insertText: String?,
        val insertTextFormat: InsertTextFormat?
    )

    @Test
    fun `Correct signatures returned`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val offset = BEGINNING_OF_FILE_OFFSET

        val completions = completionService.getCompletions(mainFileUri, offset, indexer, mainFileUri.toDocument())
        val expected = arrayOf(
            CompletionItemData(
                label = "p",
                description = "query",
                detail = "(x: integer): text",
                kind = CompletionItemKind.Function,
                insertText = "p(${'$'}{1:x})",
                insertTextFormat = InsertTextFormat.Snippet
            ),
            CompletionItemData(
                label = "abs",
                description = "function",
                detail = "(a: integer): integer",
                kind = CompletionItemKind.Function,
                insertText = "abs(${'$'}{1:a})",
                insertTextFormat = InsertTextFormat.Snippet
            ),
        )

        assertThat(completions).extracting {
            CompletionItemData(
                it.label,
                it.labelDetails.description,
                it.labelDetails.detail,
                it.kind,
                it.insertText,
                it.insertTextFormat
            )
        }.containsAll(*expected)
    }

    @Test
    fun `Declared query suggested`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val offset = BEGINNING_OF_FILE_OFFSET

        val completions = completionService.getCompletions(mainFileUri, offset, indexer, mainFileUri.toDocument())

        val expectedDeclaredFunctions = arrayOf(
            "p" to "query",
            "q" to "query",
        )
        assertThat(completions).extracting {
            Pair(it.label, it.labelDetails.description)
        }.containsAll(*expectedDeclaredFunctions)
        assertThat(completions).extracting { it.label }.doesNotContain("r")
    }

    @Test
    fun `Imported functions suggested`() {
        val importerFile = testDataBuilder.sourceFile(importerFilePath).toURI()
        val offset = BEGINNING_OF_FILE_OFFSET

        val completions = completionService.getCompletions(importerFile, offset, indexer, importerFile.toDocument())

        assertThat(completions).extracting { it.label }.contains("r")
    }

    @Test
    fun `Local variables and parameters correctly suggested`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val localVarsAndParams = arrayOf(
            "local_var" to "variable",
            "x" to "parameter"
        )

        val insideQueryBodyOffset = 66
        val completions1 = completionService.getCompletions(
            mainFileUri,
            insideQueryBodyOffset,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(completions1).extracting { it.label to it.labelDetails.description }.containsAll(*localVarsAndParams)

        val outsideQueryBody = BEGINNING_OF_FILE_OFFSET
        val completions2 = completionService.getCompletions(
            mainFileUri,
            outsideQueryBody,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(
            completions2
        ).extracting { it.label to it.labelDetails.description }.containsNone(*localVarsAndParams)
    }

    @Test
    fun `Entity members correctly suggested in create statement`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val entityMembers = arrayOf(
            "first" to "entity attribute",
            "last" to "entity attribute",
        )

        val insideCreateStatement = 180
        val completions1 = completionService.getCompletions(
            mainFileUri,
            insideCreateStatement,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(completions1).extracting { it.label to it.labelDetails.description }.containsAll(*entityMembers)

        val outsideCreateStatement = BEGINNING_OF_FILE_OFFSET
        val completions2 = completionService.getCompletions(
            mainFileUri,
            outsideCreateStatement,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(completions2).extracting { it.label to it.labelDetails.description }.containsNone(*entityMembers)
    }

    @Test
    fun `Entity members correctly suggested in at expression`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val entityMembers = arrayOf(
            ".first" to "entity attribute",
            ".last" to "entity attribute",
        )

        val insideCreateStatement = 337
        val completions1 = completionService.getCompletions(
            mainFileUri,
            insideCreateStatement,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(completions1).extracting { it.label to it.labelDetails.description }.containsAll(*entityMembers)

        val outsideCreateStatement = BEGINNING_OF_FILE_OFFSET
        val completions2 = completionService.getCompletions(
            mainFileUri,
            outsideCreateStatement,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(completions2).extracting { it.label to it.labelDetails.description }.containsNone(*entityMembers)
    }

    @Test
    fun `Entity members dot prefix is correctly trimmed in insertText`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val membersWithDotPrefix = arrayOf(".first", ".last")
        val membersWithoutDotPrefix = arrayOf("first", "last")
        val offsetWithoutDotPrefix = 337
        val offsetWithDotPrefix = 379

        val completions1 = completionService.getCompletions(
            mainFileUri,
            offsetWithoutDotPrefix,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(completions1).extracting { it.insertText }.all {
            containsAll(*membersWithDotPrefix)
            containsNone(*membersWithoutDotPrefix)
        }

        val completions2 = completionService.getCompletions(
            mainFileUri,
            offsetWithDotPrefix,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(completions2).extracting { it.insertText }.all {
            containsAll(*membersWithoutDotPrefix)
            containsNone(*membersWithDotPrefix)
        }
    }

    @Test
    fun `Namespaces members correctly suggested`() {
        val mainFileUri = testDataBuilder.mainFileUri

        val insideCreateStatement = 245
        val insideNamespaceQueries = arrayOf(
            "q2" to "query",
            "q3" to "query",
        )
        val completions1 = completionService.getCompletions(
            mainFileUri,
            insideCreateStatement,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(
            completions1
        ).extracting { it.label to it.labelDetails.description }.containsAll(*insideNamespaceQueries)

        val outsideCreateStatement = 277
        val completions2 = completionService.getCompletions(
            mainFileUri,
            outsideCreateStatement,
            indexer,
            mainFileUri.toDocument()
        )
        assertThat(completions2).extracting { it.label to it.labelDetails.description }.doesNotContain("q2")
    }

    @Test
    fun `Keywords suggested`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val offset = BEGINNING_OF_FILE_OFFSET

        val completions = completionService.getCompletions(mainFileUri, offset, indexer, mainFileUri.toDocument())

        val expectedKeywords = RellKeywords.asList().toTypedArray()
        assertThat(completions).extracting { it.label }.containsAll(*expectedKeywords)
    }

    @Test
    fun `completions suggested for imports inside {} should return only the relevant symbols`() {
        val importerFile = testDataBuilder.sourceFile(explicitImportFilePath).toURI()
        val offset = 21
        val document = Document(
            importerFile,
            version = 0,
            content = File(importerFile).readText()
        )

        val completions = completionService.getCompletions(importerFile, offset, indexer, document)

        val expectedCompletions = arrayOf(
            "fun_in_module" to "Function",
            "another_fun" to "Function",
            "userStruct" to "Struct",
            "userObject" to "Class",
            "CONSTANT" to "Constant",
            "operation1" to "Function",
            "query1" to "Function",
        )

        assertThat(completions).extracting { it.label to it.kind.name }.all {
            containsAll(*expectedCompletions)
        }
    }

    @Test
    fun `completions suggested for imports in src should not return suggestions from test files`() {
        val fileInSameModule = testDataBuilder.sourceFile(fileInSameModule).toURI()
        val offset = 5

        val completions = completionService.getCompletions(
            fileInSameModule,
            offset,
            indexer,
            fileInSameModule.toDocument()
        )

        val invalidCompletionsInsideModule = arrayOf(
            "import test.test1.*;",
            "import test.test1.{T};",
            "import test.test2.*;",
            "import test.test2.{T};",
        )

        assertThat(completions).extracting { it.label }.all {
            containsNone(*invalidCompletionsInsideModule)
        }
    }

    @Test
    fun `completions suggested for imports should not return suggestions from the same module`() {
        val fileInSameModule = testDataBuilder.sourceFile(fileInSameModule).toURI()
        val offset = 5

        val completions = completionService.getCompletions(
            fileInSameModule,
            offset,
            indexer,
            fileInSameModule.toDocument()
        )

        val invalidCompletionsInsideModule = arrayOf(
            "import a.b.*;",
            "import a.b.{T};",
        )

        val validCompletions = arrayOf(
            "import",
            "import library.*;",
            "import library.{T};",
            "import explicit_token_importer.*;",
            "import explicit_token_importer.{T};",
            "import importer.*;",
            "import importer.{T};",
            "import main.*;",
            "import main.{T};",
        )

        assertThat(completions).extracting { it.label }.all {
            containsAll(*validCompletions)
            containsNone(*invalidCompletionsInsideModule)
        }
    }

    @Test
    fun `imports inside {} in test files should include completions from other test files`() {
        val fileInSameModule = testDataBuilder.sourceFile(testFile2).toURI()
        val offset = 34
        val completions = completionService.getCompletions(
            fileInSameModule,
            offset,
            indexer,
            fileInSameModule.toDocument()
        )

        val symbolsInTestFile1 = arrayOf(
            "PI",
            "MAGIC_NUMBER"
        )

        assertThat(completions).extracting { it.label }.all {
            containsAll(*symbolsInTestFile1)
        }
    }

    @Test
    fun `imports in test files should include completions from other test files and src files`() {
        val testFile = testDataBuilder.sourceFile(testFile3).toURI()
        val offset = 15
        val completions = completionService.getCompletions(testFile, offset, indexer, testFile.toDocument())

        val completionsInsideTestFile = arrayOf(
            "import",
            "import library.*;",
            "import library.{T};",
            "import test.test1.*;",
            "import test.test1.{T};",
            "import explicit_token_importer.*;",
            "import explicit_token_importer.{T};",
            "import importer.*;",
            "import importer.{T};",
            "import main.*;",
            "import main.{T};",
            "import test.test2.*;",
            "import test.test2.{T};"
        )

        assertThat(completions).extracting { it.label }.all {
            containsAll(*completionsInsideTestFile)
        }
    }

    @Test
    fun `get correct import symbols from import with '^^^' pattern`() {
        val rellFile = testDataBuilder.sourceFile("c/d/module.rell").toURI()
        val offset = 23
        val document = Document(
            rellFile,
            version = 0,
            content = File(rellFile).readText()
        )

        val expectedCompletions = arrayOf(
            "fun_in_module" to "Function",
            "another_fun" to "Function",
            "userStruct" to "Struct",
            "userObject" to "Class",
            "CONSTANT" to "Constant",
            "operation1" to "Function",
            "query1" to "Function",
        )

        val completions = completionService.getCompletions(rellFile, offset, indexer, document)

        assertThat(completions).extracting { it.label to it.kind.name }.all {
            containsAll(*expectedCompletions)
        }
    }

    @Test
    fun `Module import completions suggested at root level`() {
        val testDataBuilder = testData(tempDir) {
            addModule(
                "module_a",
                """
                module;
                function fun_in_module() = 123;
                """.trimIndent()
            )
            addMainFile(
                """
                module;
                
                """.trimIndent()
            )
        }

        val indexer = initIndexerForTestData(testDataBuilder)
        val mainFileUri = testDataBuilder.mainFileUri
        val offset = BEGINNING_OF_FILE_OFFSET
        val completions = completionService.getCompletions(mainFileUri, offset, indexer, mainFileUri.toDocument())

        val expectedModuleImports = arrayOf(
            "import module_a.*;",
            "import module_a.{T};"
        )

        assertThat(completions).extracting { it.label }.containsAll(*expectedModuleImports)
    }

    @Test
    fun `Module import completions suggested inside namespace`() {
        val testDataBuilder = testData(tempDir) {
            addModule(
                "module_a",
                """
                module;
                function fun_in_module() = 123;
                """.trimIndent()
            )
            addMainFile(
                """
                module;
                namespace ns1 {
                    
                }
                """.trimIndent()
            )
        }

        val indexer = initIndexerForTestData(testDataBuilder)
        val mainFileUri = testDataBuilder.mainFileUri
        val offset = 28 // Position inside namespace
        val completions = completionService.getCompletions(mainFileUri, offset, indexer, mainFileUri.toDocument())

        val expectedModuleImports = arrayOf(
            "import module_a.*;",
            "import module_a.{T};"
        )

        assertThat(completions).extracting { it.label }.containsAll(*expectedModuleImports)
    }

    @Test
    fun `Module import completions suggested inside nested namespace`() {
        val testDataBuilder = testData(tempDir) {
            addModule(
                "module_a",
                """
                module;
                function fun_in_module() = 123;
                """.trimIndent()
            )
            addMainFile(
                """
                module;
                namespace ns1 {
                    namespace ns2 {
                        
                    }
                }
                """.trimIndent()
            )
        }

        val indexer = initIndexerForTestData(testDataBuilder)
        val mainFileUri = testDataBuilder.mainFileUri
        val offset = 52 // Position inside nested namespace
        val completions = completionService.getCompletions(mainFileUri, offset, indexer, mainFileUri.toDocument())

        val expectedModuleImports = arrayOf(
            "import module_a.*;",
            "import module_a.{T};"
        )

        assertThat(completions).extracting { it.label }.containsAll(*expectedModuleImports)
    }

    @Test
    fun `Module import completions not suggested inside function`() {
        val testDataBuilder = testData(tempDir) {
            addModule(
                "module_a",
                """
                module;
                function fun_in_module() = 123;
                """.trimIndent()
            )
            addMainFile(
                """
                module;
                function test() {
                    
                }
                """.trimIndent()
            )
        }

        val mainFileUri = testDataBuilder.mainFileUri
        val offset = 30 // Position inside function
        val completions = completionService.getCompletions(mainFileUri, offset, indexer, mainFileUri.toDocument())

        val invalidModuleImports = arrayOf(
            "import module_a.*;",
            "import module_a.{T};"
        )

        assertThat(completions).extracting { it.label }.containsNone(*invalidModuleImports)
    }

    private fun initIndexerForTestData(testDataBuilder: TestDataBuilder): WorkspaceIndexer {
        val indexer = WorkspaceIndexer(
            testDataBuilder.sourceFolderUri,
            RellLinter(),
            LinterOptions(),
            FormattingStyleLinter(),
            FormatterOptions(),
            testDataBuilder.workspaceFolderUri
        )
        indexer.initialFileIndexBuild()
        return indexer
    }

    private fun URI.toDocument() = Document(
        this,
        version = 0,
        content = File(this).readText()
    )

    companion object {
        private const val BEGINNING_OF_FILE_OFFSET = 0
    }
}
