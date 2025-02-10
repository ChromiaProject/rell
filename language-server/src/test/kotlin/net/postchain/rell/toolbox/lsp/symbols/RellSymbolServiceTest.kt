package net.postchain.rell.toolbox.lsp.symbols

import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.support.expected
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
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellSymbolServiceTest {
    private val rellSymbolService = RellSymbolService()
    private val rellLinter = RellLinter()
    private val formattingStyleLinter = FormattingStyleLinter()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()
    private val rellFilePath = "rell_file.rell"

    @Test
    fun `Returns empty list when resource does not exist`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            addFile(
                rellFilePath,
                """
                module;
                function main() {
                    return "main";
                }
                """.trimIndent()
            )
        }

        val indexer = WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        indexer.initialFileIndexBuild()
        val unIndexedFile = File(dir, "unindexed_file.rell").apply { writeText("") }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        val document = Document(unIndexedFile.toURI(), 1, rellFile.readText())
        val position = Position(1, 1)
        val res = rellSymbolService.getSymbolLocations(document, indexer, position)
        assertThat(res).isEmpty()
    }

    @Test
    fun `Returns empty list when symbol does not exist`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            addFile(
                rellFilePath,
                """
                module;
                function main() {
                    return "main";
                }
                """.trimIndent()
            )
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        val document = Document(rellFile.toURI(), 1, rellFile.readText())
        val indexer = WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        indexer.initialFileIndexBuild()
        val position = Position(1, 1)
        val res = rellSymbolService.getSymbolLocations(document, indexer, position)
        assertThat(res).isEmpty()
    }

    @Test
    fun `Correct document symbols returned`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            addFile(
                rellFilePath,
                """
                import .account;

                entity balance {
                  key account.account, asset;
                  mutable amount: integer;
                  chain_id: integer;
                }
                
                function create_balance_if_not_exists(account.account, asset, chain_id: integer): balance {
                  val balance_exists = balance@?{asset, account, .chain_id == chain_id};
                  return balance_exists!!;
                }
                
                operation give_balance(account_id: byte_array, asset_name: text, amount: integer, chain_id: integer) {
                  val balance = create_balance_if_not_exists(account, asset, chain_id);
                  balance.amount += amount;
                }
                
                query get_tokens_amount(id: text, asset_id: text, chain_id: integer) {
                  return balance@{chain_id}.amount;
                }
                
                namespace XXX {
                  namespace YYY {
                    query get_user_balance(id: byte_array) {
                        return balance@*{ .account.id == id } (asset_id = .asset.id);
                    }
                  }
                }
                """.trimIndent()
            )
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        val rellFileUri = rellFile.toURI()
        val document = Document(rellFile.toURI(), 1, rellFile.readText())
        val indexer = WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        indexer.initialFileIndexBuild()
        val resource = indexer.getResource(rellFileUri)!!

        val symbols = rellSymbolService.getDocumentSymbols(rellFileUri, document, resource)

        assertThat(symbols).isNotNull()
        assertThat(symbols!!.name).isEqualTo("rell_file.rell")

        val expectedChildren = arrayOf(
            "account" to SymbolKind.Package,
            "balance" to SymbolKind.Class,
            "create_balance_if_not_exists" to SymbolKind.Function,
            "give_balance" to SymbolKind.Method,
            "get_tokens_amount" to SymbolKind.Function,
            "XXX" to SymbolKind.Namespace,
        )
        assertThat(symbols.children).extracting { it.name to it.kind }.containsExactly(*expectedChildren)

        val balanceEntity = symbols.children.find { it.name == "balance" }!!
        assertThat(balanceEntity.children).extracting { it.name to it.kind }.containsExactly(
            "account" to SymbolKind.Property,
            "asset" to SymbolKind.Property,
            "amount" to SymbolKind.Property,
            "chain_id" to SymbolKind.Property,
        )

        val namespaceXXX = symbols.children.find { it.name == "XXX" }!!
        assertThat(namespaceXXX.children).extracting { it.name to it.kind }.containsExactly(
            "YYY" to SymbolKind.Namespace,
        )

        val namespaceYYY = namespaceXXX.children.find { it.name == "YYY" }!!
        assertThat(namespaceYYY.children).extracting { it.name to it.kind }.containsExactly(
            "get_user_balance" to SymbolKind.Function,
        )
    }

    @Test
    fun `Full range contains selection range`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            addFile(
                rellFilePath,
                """
                module;

                object my_name {
                    mutable name = "World";
                }
                
                operation set_name(name) {
                    my_name.name = name;
                }
                
                query hello_world() = "Hello %s!".format(my_name.name);
                
                
                
                entity
                operation send_message()
                """.trimIndent().trimEnd()
            )
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        val rellFileUri = rellFile.toURI()
        val document = Document(rellFile.toURI(), 1, rellFile.readText())
        val indexer = WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        indexer.initialFileIndexBuild()
        val resource = indexer.getResource(rellFileUri)!!

        val symbols = rellSymbolService.getDocumentSymbols(rellFileUri, document, resource)

        assertThat(symbols).isNotNull()
        symbols!!.children.forEach { symbol ->
            assertThat(symbol.selectionRange).isInBetween(symbol.range)
        }
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

        val document = Document(testDataBuilder.mainFileUri, 1, testDataBuilder.mainFile.readText())
        // TODO: investigate compiler sourcePath issue
        val indexer =
            WorkspaceIndexer(
                dir.resolve("src").toURI(),
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions
            )
        indexer.initialFileIndexBuild()

        val symbol = DocumentSymbol(
            moduleName,
            SymbolKind.Package,
            Range(Position(0, 0), Position(0, 20)),
            Range(Position(0, 9), Position(0, 17)),
        )

        val result = rellSymbolService.getSymbolInfoForImportedModule(symbol, document, indexer)

        assertThat(result.size).isEqualTo(1)
        with(result[0]) {
            assertThat(kind).isEqualTo(IdeSymbolKind.DEF_FUNCTION)
            assertThat(defId!!.encode()).isEqualTo("function[$functionName]")
        }
    }

    @Test
    fun `get correct IdeSymbolInfo for cached resource`(@TempDir dir: File) {
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
            rellLinter,
            formattingStyleLinter,
            RellFormatterOptionsResolver(),
            RellLinterOptionsResolver()
        )

        val document = Document(testDataBuilder.mainFileUri, 1, testDataBuilder.mainFile.readText())
        val indexer = WorkspaceIndexer(
            dir.resolve("src").toURI(),
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions
        ).apply { initialFileIndexBuild() }

        val restoredIndexer = WorkspaceIndexer(
            dir.resolve("src").toURI(),
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions
        ).apply {
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

        val result = rellSymbolService.getSymbolInfoForImportedModule(symbol, document, restoredIndexer)

        assertThat(result.size).isEqualTo(1)
        with(result[0]) {
            assertThat(doc).isNotNull()
            assertThat(doc!!.kind).isEqualTo(DocSymbolKind.FUNCTION)
        }
    }

    @Test
    fun `find active import symbol from position within '{}' of root import`(@TempDir dir: File) {
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
                import a.$moduleName.{};
                """.trimIndent()
            )
        }

        val document = Document(testDataBuilder.mainFileUri, 1, testDataBuilder.mainFile.readText())
        val indexer = WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        indexer.initialFileIndexBuild()

        val offSetWithinCurlyBrackets = 19 // import a.$moduleName.{};
        val symbol = rellSymbolService.getActiveImportSymbol(
            testDataBuilder.mainFileUri,
            offSetWithinCurlyBrackets,
            document,
            indexer
        )

        assertThat(symbol!!.kind).isEqualTo(SymbolKind.Package)
        assertThat(symbol.name).isEqualTo(moduleName)
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

        val document = Document(testDataBuilder.mainFileUri, 1, testDataBuilder.mainFile.readText())
        val indexer = WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        indexer.initialFileIndexBuild()

        val offSetWithinCurlyBrackets = 38 // import a.$moduleName.{};
        val symbol = rellSymbolService.getActiveImportSymbol(
            testDataBuilder.mainFileUri,
            offSetWithinCurlyBrackets,
            document,
            indexer
        )

        assertThat(symbol!!.kind).isEqualTo(SymbolKind.Package)
        assertThat(symbol.name).isEqualTo(moduleName)
    }

    @Test
    fun `find active import symbol from position within '{}' in nested namespace`(@TempDir dir: File) {
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
                namespace ns1 {
                    namespace ns2 {
                        import a.$moduleName.{};
                    }
                }
                """.trimIndent()
            )
        }

        val document = Document(testDataBuilder.mainFileUri, 1, testDataBuilder.mainFile.readText())
        val indexer = WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        indexer.initialFileIndexBuild()

        val offSetWithinCurlyBrackets = 63 // import a.$moduleName.{};
        val symbol = rellSymbolService.getActiveImportSymbol(
            testDataBuilder.mainFileUri,
            offSetWithinCurlyBrackets,
            document,
            indexer
        )

        assertThat(symbol!!.kind).isEqualTo(SymbolKind.Package)
        assertThat(symbol.name).isEqualTo(moduleName)
    }

    @Test
    fun `test getActiveImportSymbol returns null for non-package symbol`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            addMainFile(
                """
                module;
                function fun_in_module() = 123;
                """.trimIndent()
            )
        }

        val document = Document(testDataBuilder.mainFileUri, 1, testDataBuilder.mainFile.readText())
        val indexer = WorkspaceIndexer(dir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        indexer.initialFileIndexBuild()

        val offSetOnFunInModule = 24 // import a.$moduleName.{};
        val symbol = rellSymbolService.getActiveImportSymbol(
            testDataBuilder.mainFileUri,
            offSetOnFunInModule,
            document,
            indexer
        )

        assertThat(symbol).isNull()
    }
}

fun Assert<Range>.isInBetween(full: Range) = given { part ->
    val between = when {
        part.start.line < full.start.line -> false
        part.start.line == full.start.line && part.start.character < full.start.character -> false
        part.end.line > full.end.line -> false
        part.end.line == full.end.line && part.end.character > full.end.character -> false
        else -> true
    }
    if (between) {
        return
    }
    expected("$full to contain $part")
}
