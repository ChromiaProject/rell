package net.postchain.rell.toolbox.lsp.symbols

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.doesNotContain
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellWorkspaceSymbolsTest {
    private val rellSymbolService = RellSymbolService()
    private val rellLinter = RellLinter()
    private val formattingStyleLinter = FormattingStyleLinter()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()

    private val firstFilePath = "first.rell"
    private val secondFilePath = "second.rell"

    @TempDir
    private lateinit var tempDir: File
    private lateinit var testDataBuilder: TestDataBuilder
    private lateinit var indexers: List<WorkspaceIndexer>

    @BeforeEach
    fun setup() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                function main() {
                    return "main";
                }
                """.trimIndent()
            )
            addFile(
                firstFilePath,
                """
                module;
                val SOME_CONSTANT = 42;

                function main() {
                    return "main";
                }
                """.trimIndent()
            )
            addFile(
                secondFilePath,
                """
                module;
                import first;
                query another() {
                    return "another";
                }
                """.trimIndent()
            )
        }

        val indexer = WorkspaceIndexer(
            testDataBuilder.sourceFolderUri,
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions
        )
        indexer.initialFileIndexBuild()
        indexers = listOf(indexer)
    }

    @Test
    fun `Returns workspace symbols matching query`() {
        val symbols = rellSymbolService.getWorkspaceSymbols("main", indexers)

        assertThat(symbols).containsExactlyInAnyOrder(
            WorkspaceSymbol(
                "main",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.mainFileUri.toString(),
                        Range(Position(1, 9), Position(1, 13))
                    )
                )
            ),
            WorkspaceSymbol(
                "main",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(firstFilePath).toURI().toString(),
                        Range(Position(3, 9), Position(3, 13))
                    )
                )
            )
        )
    }

    @Test
    fun `Returns empty list when no symbols match query`() {
        val symbols = rellSymbolService.getWorkspaceSymbols("non_existent", indexers)
        assertThat(symbols).isEmpty()
    }

    @Test
    fun `Returns all workspace symbols when query is empty`() {
        val symbols = rellSymbolService.getWorkspaceSymbols("", indexers)
        assertThat(symbols).containsExactlyInAnyOrder(
            WorkspaceSymbol(
                "main",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.mainFileUri.toString(),
                        Range(Position(1, 9), Position(1, 13))
                    )
                )
            ),
            WorkspaceSymbol(
                "main",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(firstFilePath).toURI().toString(),
                        Range(Position(3, 9), Position(3, 13))
                    )
                )
            ),
            WorkspaceSymbol(
                "another",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(secondFilePath).toURI().toString(),
                        Range(Position(2, 6), Position(2, 13))
                    )
                )
            ),
            WorkspaceSymbol(
                "SOME_CONSTANT",
                SymbolKind.Constant,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(firstFilePath).toURI().toString(),
                        Range(Position(1, 4), Position(1, 17))
                    )
                )
            ),
        )
    }

    @Test
    fun `Skips import symbols`() {
        val symbols = rellSymbolService.getWorkspaceSymbols("", indexers)
        assertThat(symbols).extracting { it.name to it.kind }.doesNotContain("first" to SymbolKind.Package)
    }

    @Test
    fun `Returns all symbols from multiple workspaces when query is empty`() {
        val anotherWorkspaceBuilder = testData(tempDir.resolve("another_workspace")) {
            addMainFile(
                """
                module;
                function main() {
                    return "main";
                }
                """.trimIndent()
            )
        }
        val anotherIndexer = WorkspaceIndexer(
            anotherWorkspaceBuilder.sourceFolderUri,
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions
        )
        anotherIndexer.initialFileIndexBuild()
        val multiWorkspaceIndexers = indexers + anotherIndexer

        val symbols = rellSymbolService.getWorkspaceSymbols("", multiWorkspaceIndexers)

        assertThat(symbols).containsExactlyInAnyOrder(
            WorkspaceSymbol(
                "main",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.mainFileUri.toString(),
                        Range(Position(1, 9), Position(1, 13))
                    )
                )
            ),
            WorkspaceSymbol(
                "main",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(firstFilePath).toURI().toString(),
                        Range(Position(3, 9), Position(3, 13))
                    )
                )
            ),
            WorkspaceSymbol(
                "another",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(secondFilePath).toURI().toString(),
                        Range(Position(2, 6), Position(2, 13))
                    )
                )
            ),
            WorkspaceSymbol(
                "SOME_CONSTANT",
                SymbolKind.Constant,
                Either.forLeft(
                    Location(
                        testDataBuilder.sourceFile(firstFilePath).toURI().toString(),
                        Range(Position(1, 4), Position(1, 17))
                    )
                )
            ),
            WorkspaceSymbol(
                "main",
                SymbolKind.Function,
                Either.forLeft(
                    Location(
                        anotherWorkspaceBuilder.mainFileUri.toString(),
                        Range(Position(1, 9), Position(1, 13))
                    )
                )
            ),
        )
    }
}
