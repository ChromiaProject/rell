package net.postchain.rell.toolbox.lsp.symbols

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

class RellCompletionSymbolServiceTest {
    @TempDir
    private lateinit var tempDir: File
    private lateinit var testDataBuilder: TestDataBuilder
    private lateinit var indexer: WorkspaceIndexer
    private val completionSymbolService = RellCompletionSymbolService(RellSymbolService())

    @BeforeEach
    fun setup() {
        testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                
                import lib.something;
                import other.module;
                
                namespace ns {
                    
                    function test() {}
                }
                """.trimIndent()
            )
        }
        indexer = createIndexer(testDataBuilder.workspaceFolderUri)
    }

    @Test
    fun `getActiveImportSymbol finds import symbol`() {
        val fileUri = testDataBuilder.mainFileUri
        val document = fileUri.toDocument()
        val offset = document.getOffSet(Position(2, 15)) // Position at "something" in first import

        val symbol = completionSymbolService.getActiveImportSymbol(fileUri, offset, document, indexer)

        assertThat(symbol).isNotNull()
        assertThat(symbol!!.kind).isEqualTo(SymbolKind.Package)
        assertThat(symbol.name).isEqualTo("something")
    }

    @Test
    fun `findEnclosingFileOrNamespace returns namespace from empty position inside namespace`() {
        val fileUri = testDataBuilder.mainFileUri
        val document = fileUri.toDocument()
        val resource = indexer.getResource(fileUri)!!
        val offset = document.getOffSet(Position(6, 2)) // Position inside namespace

        val symbol = completionSymbolService.findEnclosingFileOrNamespace(fileUri, document, resource, offset)

        assertThat(symbol).isNotNull()
        assertThat(symbol!!.kind).isEqualTo(SymbolKind.Namespace)
        assertThat(symbol.name).isEqualTo("ns")
    }

    @Test
    fun `findEnclosingFileOrNamespace returns null from child position inside namespace`() {
        val fileUri = testDataBuilder.mainFileUri
        val document = fileUri.toDocument()
        val resource = indexer.getResource(fileUri)!!
        val offset = document.getOffSet(Position(7, 9)) // Position on function inside namespace

        val symbol = completionSymbolService.findEnclosingFileOrNamespace(fileUri, document, resource, offset)

        assertThat(symbol).isNull()
    }

    @Test
    fun `test findEnclosingFileOrNamespace returns file from empty position inside root of file`() {
        val fileUri = testDataBuilder.mainFileUri
        val document = fileUri.toDocument()
        val resource = indexer.getResource(fileUri)!!
        val offset = document.getOffSet(Position(1, 0)) // Position at imports

        val symbol = completionSymbolService.findEnclosingFileOrNamespace(fileUri, document, resource, offset)

        assertThat(symbol).isNotNull()
        assertThat(symbol!!.kind).isEqualTo(SymbolKind.File)
        assertThat(symbol.name).isEqualTo("main.rell")
    }

    @Test
    fun `test findEnclosingFileOrNamespace returns null from child position inside file`() {
        val fileUri = testDataBuilder.mainFileUri
        val document = fileUri.toDocument()
        val resource = indexer.getResource(fileUri)!!
        val offset = document.getOffSet(Position(2, 0)) // Position at imports

        val symbol = completionSymbolService.findEnclosingFileOrNamespace(fileUri, document, resource, offset)

        assertThat(symbol).isNull()
    }

    @Test
    fun `test getSymbolInfoForImportedModule with cached resource`(@TempDir dir: File) {
        val moduleName = "module_b"
        val functionName = "fun_in_module"
        val testDataBuilder = testData(dir) {
            addModule(
                "a/$moduleName",
                """
                function $functionName() = 123;
                """.trimIndent()
            )
            addMainFile(
                """
                import a.$moduleName.{};
                """.trimIndent()
            )
        }
        val indexSerializer = RellIndexSerializer(
            RellLinter(),
            FormattingStyleLinter(),
            RellFormatterOptionsResolver(),
            RellLinterOptionsResolver()
        )

        val document = testDataBuilder.mainFileUri.toDocument()
        val indexer = createIndexer(dir.resolve("src").toURI(), false).apply { initialFileIndexBuild() }

        val restoredIndexer = createIndexer(dir.resolve("src").toURI(), false).apply {
            val fromCache = indexSerializer.deserializeAsWorkspaceIndexer(indexSerializer.serializeAsBytes(indexer))
            initialFileIndexBuild(fromCache)
        }

        restoredIndexer.initialFileIndexBuild()
        restoredIndexer.updateFileUriResourceMap(testDataBuilder.mainFileUri)

        val symbol = DocumentSymbol(
            moduleName,
            SymbolKind.Package,
            Range(Position(0, 0), Position(0, 20)),
            Range(Position(0, 9), Position(0, 17)),
        )

        val result = completionSymbolService.getSymbolInfoForImportedModule(symbol, document, restoredIndexer)

        assertThat(result.size).isEqualTo(1)
        with(result[0]) {
            assertThat(doc).isNotNull()
            assertThat(doc!!.kind).isEqualTo(DocSymbolKind.FUNCTION)
        }
    }

    @Test
    fun `find active import symbol from position within '{}' in namespace`(@TempDir dir: File) {
        val moduleName = "module_b"
        val testDataBuilder = testData(dir) {
            addModule(
                "a/$moduleName",
                """
                module;
                function fun_in_module() = 123;
                """.trimIndent()
            )
            addMainFile(
                """
                namespace ns {
                    import a.$moduleName.{};
                }
                """.trimIndent()
            )
        }

        val document = testDataBuilder.mainFileUri.toDocument()
        val indexer = createIndexer(dir.toURI())

        val offSetWithinCurlyBrackets = 38 // import a.$moduleName.{};
        val symbol = completionSymbolService.getActiveImportSymbol(
            testDataBuilder.mainFileUri,
            offSetWithinCurlyBrackets,
            document,
            indexer
        )

        assertThat(symbol!!.kind).isEqualTo(SymbolKind.Package)
        assertThat(symbol.name).isEqualTo(moduleName)
    }

    @Test
    fun `get correct IdeSymbolInfo for import symbols inside '{}'`(@TempDir dir: File) {
        val moduleName = "module_b"
        val functionName = "fun_in_module"
        val testDataBuilder = testData(dir) {
            addModule(
                "a/$moduleName",
                """
                function $functionName() = 123;
                """.trimIndent()
            )
            addMainFile(
                """
                import a.$moduleName.{};
                """.trimIndent()
            )
        }

        val document = testDataBuilder.mainFileUri.toDocument()
        val indexer = createIndexer(dir.resolve("src").toURI())

        val symbol = DocumentSymbol(
            moduleName,
            SymbolKind.Package,
            Range(Position(0, 0), Position(0, 20)),
            Range(Position(0, 9), Position(0, 17)),
        )

        val result = completionSymbolService.getSymbolInfoForImportedModule(symbol, document, indexer)

        assertThat(result.size).isEqualTo(1)
        with(result[0]) {
            assertThat(kind).isEqualTo(IdeSymbolKind.DEF_FUNCTION)
            assertThat(defId!!.encode()).isEqualTo("function[$functionName]")
        }
    }

    private fun createIndexer(workspaceUri: URI, initialize: Boolean = true): WorkspaceIndexer {
        val indexer = WorkspaceIndexer(
            workspaceUri,
            RellLinter(),
            LinterOptions(),
            FormattingStyleLinter(),
            FormatterOptions(),
            testDataBuilder.workspaceFolderUri
        )
        if (initialize) {
            indexer.initialFileIndexBuild()
        }

        return indexer
    }

    private fun URI.toDocument() = Document(
        this,
        version = 0,
        content = File(this).readText()
    )
}
