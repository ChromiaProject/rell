package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import util.TestClient
import util.TestClientServerLauncher
import util.TestPosition
import util.TestPrepareRenameResult
import util.TestRange
import util.TestServerModule
import util.TestTextEdit

class RellSymbolRenameTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
    private lateinit var testWorkspaceSrc: File
    private val serverModule = TestServerModule()

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setupBeforeEach() {
        val koinApp = serverModule.startKoin()

        clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        testClient = clientServerLauncher.testClient
        server = koinApp.koin.get<RellLanguageServer>()
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()

        val rellRenameFolderDir = File(javaClass.classLoader.getResource("symbolRename")!!.file)
        val tempCopyDestination = tempDir.resolve("symbolRename")
        await().until { rellRenameFolderDir.copyRecursively(tempCopyDestination) }

        testWorkspaceSrc = tempCopyDestination.resolve("src")
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `Renaming isn't allowed for restricted symbols`() {
        clientServerLauncher.initializeServer(testWorkspaceSrc.toURI())
        val renamedSymbolFile = testWorkspaceSrc.resolve("restricted.rell")
        val fileUri = openFile(renamedSymbolFile)

        val restrictedSymbolPositions = listOf(
            Position(1, 10),
            Position(2, 10),
            Position(2, 24),
            Position(4, 6),
            Position(6, 35),
        )

        restrictedSymbolPositions.forEach { position ->
            val result = server.prepareRename(
                PrepareRenameParams(
                    TextDocumentIdentifier(fileUri),
                    position
                )
            ).join()

            assertThat(result.first).isNull()
            assertThat(result.second).isNull()
            assertThat(result.third).isEqualTo(PrepareRenameDefaultBehavior())
        }
    }

    @Test
    fun `Symbol is renamed in whole workspace`() {
        clientServerLauncher.initializeServer(testWorkspaceSrc.toURI())
        val renamedSymbolFile = testWorkspaceSrc.resolve("main.rell")
        val fileUri = openFile(renamedSymbolFile)

        val oldName = "better_addition"
        val newFunctionName = "newName"

        val prepareResult = server.prepareRename(prepareRenameParams(fileUri, Position(4, 15))).join()
        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(4, 9), TestPosition(4, 24)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val result = server.rename(renameParams(fileUri, Position(4, 15), "newName")).join()
        val anotherImportingFileUri = testWorkspaceSrc.resolve("submodule/another_importing.rell").toURI().toString()
        assertThat(result.changes.keys).containsOnly(fileUri, anotherImportingFileUri)
        assertThat(result.changes[fileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 9), TestPosition(4, 24)), newFunctionName),
            TestTextEdit(TestRange(TestPosition(14, 17), TestPosition(14, 32)), newFunctionName)
        )
        assertThat(result.changes[anotherImportingFileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 17), TestPosition(2, 32)), newFunctionName)
        )
    }

    @Test
    fun `Renaming symbol in non rell file is ignored`() {
        clientServerLauncher.initializeServer(testWorkspaceSrc.toURI())
        val renamedSymbolFile = testWorkspaceSrc.resolve("main.js")
        val fileUri = openFile(renamedSymbolFile)

        val prepareResult = server.prepareRename(prepareRenameParams(fileUri, Position(0, 11))).join()
        assertThat(prepareResult.first).isNull()
        assertThat(prepareResult.second).isNull()
        assertThat(prepareResult.third).isEqualTo(PrepareRenameDefaultBehavior(true))

        val result = server.rename(renameParams(fileUri, Position(0, 11), "newName")).join()
        assertThat(result.changes).isEmpty()
    }

    @Test
    fun `Rename enum with value with unqualified name`(@TempDir tempDir: Path) {
        val rellFile = tempDir.resolve("rell/src/main.rell")
        rellFile.createParentDirectories()
        rellFile.writeText(
            """
            module;
            
            struct my_struct1 {
                a_property: text;
                my_enum;
            }
            
            function f1(my_struct1) = my_struct1.my_enum;
            
            struct my_struct2 {
                a_property: text;
                my_enum: my_enum;
            }
            
            function f2(my_struct2) = my_struct2.my_enum;
            
            struct my_struct3 {
                a_property: text;
                ref_property: my_enum;
            }
            
            function f3(my_struct3) = my_struct3.ref_property;
                            
            enum my_enum {
                foo,
                bar
            }
            """.trimIndent()
        )
        clientServerLauncher.initializeServer(tempDir.toFile().toURI())
        val fileUri = openFile(rellFile.toFile())
        val oldName = "my_enum"
        val newName = "something_else"

        val prepareResult = server.prepareRename(prepareRenameParams(fileUri, Position(23, 5))).join()
        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(23, 5), TestPosition(23, 12)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val result = server.rename(renameParams(fileUri, Position(23, 5), newName)).join()
        assertThat(result.changes[fileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 11)), "my_enum: $newName"),
            TestTextEdit(TestRange(TestPosition(11, 13), TestPosition(11, 20)), newName),
            TestTextEdit(TestRange(TestPosition(18, 18), TestPosition(18, 25)), newName),
            TestTextEdit(TestRange(TestPosition(23, 5), TestPosition(23, 12)), newName),
        )
    }

    @Test
    fun `Rename struct with attribute with unqualified name`(@TempDir tempDir: Path) {
        val rellFile = tempDir.resolve("rell/src/main.rell")
        rellFile.createParentDirectories()
        rellFile.writeText(
            """
            module;
            
            struct my_struct1 {
                a_property: text;
                another_struct;
            }
            
            function f1(my_struct1) = my_struct1.another_struct.another_property;
            
            struct my_struct2 {
                a_property: text;
                another_struct: another_struct;
            }
            
            function f2(my_struct2) = my_struct2.another_struct.another_property;
            
            struct my_struct3 {
                a_property: text;
                ref_property: another_struct;
            }
            
            function f3(my_struct3) = my_struct3.ref_property.another_property;
                            
            struct another_struct {
                another_property: text;
            }
            """.trimIndent()
        )
        clientServerLauncher.initializeServer(tempDir.toFile().toURI())
        val fileUri = openFile(rellFile.toFile())
        val oldName = "another_struct"
        val newName = "something_else"

        val prepareResult = server.prepareRename(prepareRenameParams(fileUri, Position(23, 9))).join()
        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(23, 7), TestPosition(23, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()


        val result = server.rename(renameParams(fileUri, Position(23, 9), newName)).join()
        assertThat(result.changes[fileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 18)), "another_struct: $newName"),
            TestTextEdit(TestRange(TestPosition(11, 20), TestPosition(11, 34)), newName),
            TestTextEdit(TestRange(TestPosition(18, 18), TestPosition(18, 32)), newName),
            TestTextEdit(TestRange(TestPosition(23, 7), TestPosition(23, 21)), newName),
        )
    }

    @Test
    fun `Rename entity with attribute with unqualified name`(@TempDir tempDir: Path) {
        val rellFile = tempDir.resolve("rell/src/main.rell")
        rellFile.createParentDirectories()
        rellFile.writeText(
            """
            module;
            
            entity my_entity1 {
                a_property: text;
                another_entity;
            }
            
            function q1() = my_entity1 @* { .another_entity.another_property == "test" };
            
            entity my_entity2 {
                a_property: text;
                another_entity: another_entity;
            }
            
            function q2() = my_entity2 @* { .another_entity.another_property == "test" };
            
            entity my_entity3 {
                a_property: text;
                ref_property: another_entity;
            }
            
            function q3() = my_entity3 @* { .ref_property.another_property == "test" };
            
            entity another_entity {
                another_property: text;
            }
            """.trimIndent()
        )
        clientServerLauncher.initializeServer(tempDir.toFile().toURI())
        val fileUri = openFile(rellFile.toFile())

        val oldName = "another_entity"
        val newName = "something_else"

        val prepareResult = server.prepareRename(prepareRenameParams(fileUri, Position(23, 9))).join()
        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(23, 7), TestPosition(23, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val result = server.rename(renameParams(fileUri, Position(23, 9), newName)).join()
        assertThat(result.changes[fileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 18)), "another_entity: $newName"),
            TestTextEdit(TestRange(TestPosition(11, 20), TestPosition(11, 34)), newName),
            TestTextEdit(TestRange(TestPosition(18, 18), TestPosition(18, 32)), newName),
            TestTextEdit(TestRange(TestPosition(23, 7), TestPosition(23, 21)), newName),
        )
    }

    @Test
    fun `Rename entity with attribute with qualified name`() {
        clientServerLauncher.initializeServer(testWorkspaceSrc.toURI())
        val oldName = "another_entity"
        val newName = "something_else"

        val entityModuleFileUri = openFile(testWorkspaceSrc.resolve("entity_module.rell"))
        val prepareResult = server.prepareRename(prepareRenameParams(entityModuleFileUri, Position(2, 10))).join()
        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(2, 7), TestPosition(2, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val fileUri = testWorkspaceSrc.resolve("main.rell").toURI().toString()
        val result = server.rename(renameParams(entityModuleFileUri, Position(2, 10), newName)).join()
        assertThat(result.changes.keys).containsOnly(fileUri, entityModuleFileUri)
        assertThat(result.changes[fileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(
                TestRange(TestPosition(19, 4), TestPosition(19, 32)),
                "another_entity: entity_module.$newName"
            ),
            TestTextEdit(TestRange(TestPosition(26, 34), TestPosition(26, 48)), newName),
            TestTextEdit(TestRange(TestPosition(33, 32), TestPosition(33, 46)), newName),
        )
        assertThat(result.changes[entityModuleFileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 7), TestPosition(2, 21)), newName),
        )
    }

    @Test
    fun `Rename with import alias`(@TempDir tempDir: Path) {
        val oldName = "foo_entity"

        val entityModule = tempDir.resolve("rell/src/entity_module/module.rell").createParentDirectories()
        entityModule.writeText(
            """
            module;

            entity $oldName {
                name;
            }
            """.trimIndent()
        )

        val rellFile = tempDir.resolve("rell/src/main.rell").createParentDirectories()
        rellFile.createParentDirectories()
        rellFile.writeText(
            """
            module;
            
            import a: entity_module.{$oldName};
            """.trimIndent()
        )

        clientServerLauncher.initializeServer(tempDir.toFile().toURI())
        val fileUri = openFile(rellFile.toFile())
        val positionForRenaming = Position(2, 30)

        val prepareResult = server.prepareRename(prepareRenameParams(fileUri, Position(2, 30))).join()
        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(2, 7), TestPosition(2, 17)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()


        val newName = "something_else"
        val result = server.rename(renameParams(fileUri, positionForRenaming, newName)).join()
        assertThat(result.changes.keys).containsOnly(fileUri, entityModule.toFileUri())
        assertThat(result.changes[fileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 25), TestPosition(2, 35)), newName),
        )
        assertThat(result.changes[entityModule.toFileUri()]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 7), TestPosition(2, 17)), newName),
        )
    }

    private fun openFile(file: File): String {
        val fileUri = file.toURI().toString()
        val textDocumentItem = TextDocumentItem(fileUri, "rell", 1, file.readText())
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.didOpen(didOpenParam)
        return fileUri
    }

    private fun prepareRenameParams(fileUri: String, position: Position) =
        PrepareRenameParams(TextDocumentIdentifier(fileUri), position)

    private fun renameParams(fileUri: String, position: Position, newName: String) =
        RenameParams(TextDocumentIdentifier(fileUri), position, newName)

    private fun Path.toFileUri() = this.toFile().toURI().toString()
}
