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
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.testing.testData
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
    val rellFilePath = "rell_file.rell"

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

        assertThat(symbols.size).isEqualTo(1)
        assertThat(symbols[0].left).isNull()

        val root = symbols[0].right
        assertThat(root).isNotNull()
        assertThat(root.name).isEqualTo("rell_file.rell")

        val expectedChildren = arrayOf(
            "account" to SymbolKind.Package,
            "balance" to SymbolKind.Class,
            "create_balance_if_not_exists" to SymbolKind.Function,
            "give_balance" to SymbolKind.Method,
            "get_tokens_amount" to SymbolKind.Function,
            "XXX" to SymbolKind.Namespace,
        )
        assertThat(root.children).extracting { it.name to it.kind }.containsExactly(*expectedChildren)

        val balanceEntity = root.children.find { it.name == "balance" }!!
        assertThat(balanceEntity.children).extracting { it.name to it.kind }.containsExactly(
            "account" to SymbolKind.Property,
            "asset" to SymbolKind.Property,
            "amount" to SymbolKind.Property,
            "chain_id" to SymbolKind.Property,
        )

        val namespaceXXX = root.children.find { it.name == "XXX" }!!
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

        val root = symbols[0].right
        root.children.forEach { symbol ->
            assertThat(symbol.selectionRange).isInBetween(symbol.range)
        }
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
