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
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellCompletionServiceTest {

    @TempDir
    private lateinit var tempDir: File
    private lateinit var testDataBuilder: TestDataBuilder
    private lateinit var indexer: WorkspaceIndexer
    private val completionService = RellCompletionService()
    private val importerFilePath = "importer.rell"
    private val libraryFilePath = "library.rell"

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
                """.trimIndent()
            )
            addFile(libraryFilePath, "module; function r(i: integer) = 123;")
            addFile(importerFilePath, "module; import library.*;")
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

        val completions = completionService.getCompletions(mainFileUri, offset, indexer)
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

        val completions = completionService.getCompletions(mainFileUri, offset, indexer)
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

        val completions = completionService.getCompletions(mainFileUri, offset, indexer)

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

        val completions = completionService.getCompletions(importerFile, offset, indexer)

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
        val completions1 = completionService.getCompletions(mainFileUri, insideQueryBodyOffset, indexer)
        assertThat(completions1).extracting { it.label to it.labelDetails.description }.containsAll(*localVarsAndParams)

        val outsideQueryBody = BEGINNING_OF_FILE_OFFSET
        val completions2 = completionService.getCompletions(mainFileUri, outsideQueryBody, indexer)
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
        val completions1 = completionService.getCompletions(mainFileUri, insideCreateStatement, indexer)
        assertThat(completions1).extracting { it.label to it.labelDetails.description }.containsAll(*entityMembers)

        val outsideCreateStatement = BEGINNING_OF_FILE_OFFSET
        val completions2 = completionService.getCompletions(mainFileUri, outsideCreateStatement, indexer)
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
        val completions1 = completionService.getCompletions(mainFileUri, insideCreateStatement, indexer)
        assertThat(completions1).extracting { it.label to it.labelDetails.description }.containsAll(*entityMembers)

        val outsideCreateStatement = BEGINNING_OF_FILE_OFFSET
        val completions2 = completionService.getCompletions(mainFileUri, outsideCreateStatement, indexer)
        assertThat(completions2).extracting { it.label to it.labelDetails.description }.containsNone(*entityMembers)
    }

    @Test
    fun `Entity members dot prefix is correctly trimmed in insertText`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val membersWithDotPrefix = arrayOf(".first", ".last")
        val membersWithoutDotPrefix = arrayOf("first", "last")
        val offset = 337

        val completions1 = completionService.getCompletions(mainFileUri, offset, indexer, trimPrefixDot = false)
        assertThat(completions1).extracting { it.insertText }.all {
            containsAll(*membersWithDotPrefix)
            containsNone(*membersWithoutDotPrefix)
        }

        val completions2 = completionService.getCompletions(mainFileUri, offset, indexer, trimPrefixDot = true)
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
        val completions1 = completionService.getCompletions(mainFileUri, insideCreateStatement, indexer)
        assertThat(
            completions1
        ).extracting { it.label to it.labelDetails.description }.containsAll(*insideNamespaceQueries)

        val outsideCreateStatement = 277
        val completions2 = completionService.getCompletions(mainFileUri, outsideCreateStatement, indexer)
        assertThat(completions2).extracting { it.label to it.labelDetails.description }.doesNotContain("q2")
    }

    @Test
    fun `Keywords suggested`() {
        val mainFileUri = testDataBuilder.mainFileUri
        val offset = BEGINNING_OF_FILE_OFFSET

        val completions = completionService.getCompletions(mainFileUri, offset, indexer)

        val expectedKeywords = RellKeywords.asList().toTypedArray()
        assertThat(completions).extracting { it.label }.containsAll(*expectedKeywords)
    }

    companion object {
        private const val BEGINNING_OF_FILE_OFFSET = 0
    }
}
