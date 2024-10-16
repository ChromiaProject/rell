package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.toolbox.lsp.TestClient
import net.postchain.rell.toolbox.lsp.TestClientServerLauncher
import net.postchain.rell.toolbox.lsp.TestPosition
import net.postchain.rell.toolbox.lsp.TestPrepareRenameResult
import net.postchain.rell.toolbox.lsp.TestRange
import net.postchain.rell.toolbox.lsp.TestServerModule
import net.postchain.rell.toolbox.lsp.TestTextEdit
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextEdit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.io.path.writeText

class RellSymbolRenameTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient

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
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `Renaming isn't allowed for restricted symbols`(@TempDir tempDir: Path) {
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                import submodule.*;
                import somealias: ^.submodule;
                
                @extendable function some_extendable(amount: integer);
                
                function other_addition(paramA: integer, paramB: integer): integer {
                    require(paramA > 0);
                    val localA = paramA;
                    val localB = paramB;
                
                    val localC = localA + paramA;
                
                    return localC;
                }                    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)

        val restrictedSymbolPositions = listOf(
            Position(1, 10),
            Position(2, 10),
            Position(2, 24),
            Position(4, 6),
            Position(6, 35),
            Position(7, 4),
        )

        restrictedSymbolPositions.forEach { position ->
            val result = server.prepareRename(
                PrepareRenameParams(
                    TextDocumentIdentifier(fileOfRenamingEvent),
                    position
                )
            ).join()

            assertThat(result.first).isNull()
            assertThat(result.second).isNull()
            assertThat(result.third).isEqualTo(PrepareRenameDefaultBehavior())
        }
    }

    @Test
    fun `Symbol is renamed in open file and affected file inside submodule`(@TempDir tempDir: Path) {
        val submoduleName = "submodule"
        val affectedFilePath = "$submoduleName/another_importing.rell"
        val oldName = "better_addition"

        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                import submodule.*;
                
                function $oldName(paramA: integer, paramB: integer): integer {
                    val localA = paramA;
                    val localB = paramB;
                
                    val localC = localA + paramA;
                
                    return localC;
                }
                                    
                function local_reference() {
                    val result = $oldName(4, 6);
                }                                    
                """.trimIndent()
            )
            addFile(
                affectedFilePath,
                """
                function another_reference_from_submodule() {
                    val result = $oldName(24, 343);
                }                    
                """.trimIndent()
            )
            addFile(
                "$submoduleName/module.rell",
                """
                module;
                
                import ^.main.*;                    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, Position(3, 15))).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(3, 9), TestPosition(3, 24)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "newName"
        val affectedFile = testDataBuilder.sourceFile(affectedFilePath).toFileUri()
        val result = server.rename(renameParams(fileOfRenamingEvent, Position(3, 15), newName)).join()

        assertThat(result.changes.keys).containsOnly(fileOfRenamingEvent, affectedFile)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(3, 9), TestPosition(3, 24)), newName),
            TestTextEdit(TestRange(TestPosition(13, 17), TestPosition(13, 32)), newName)
        )
        assertThat(result.changes[affectedFile]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(1, 17), TestPosition(1, 32)), newName)
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Renaming symbol in non rell file is ignored`(@TempDir tempDir: Path) {
        val testFilePath = "main.js"
        val testDataBuilder = testData(tempDir) {
            addFile(
                testFilePath,
                """
                function not_rell() {
                    console.log("not_rell");
                }
                
                not_rell();
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.sourceFile(testFilePath))

        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, Position(0, 11))).join()
        assertThat(prepareResult.first).isNull()
        assertThat(prepareResult.second).isNull()
        assertThat(prepareResult.third).isEqualTo(PrepareRenameDefaultBehavior(true))

        val result = server.rename(renameParams(fileOfRenamingEvent, Position(0, 11), "newName")).join()
        assertThat(result.changes).isEmpty()
    }

    @Test
    fun `Rename enum with value with unqualified name`(@TempDir tempDir: Path) {
        val oldName = "my_enum"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                
                struct my_struct1 {
                    a_property: text;
                    $oldName;
                }
                
                function f1(my_struct1) = my_struct1.$oldName;
                
                struct my_struct2 {
                    a_property: text;
                    $oldName: $oldName;
                }
                
                function f2(my_struct2) = my_struct2.$oldName;
                
                struct my_struct3 {
                    a_property: text;
                    ref_property: $oldName;
                }
                
                function f3(my_struct3) = my_struct3.ref_property;
                                
                enum $oldName {
                    foo,
                    bar
                }                    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, Position(23, 5))).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(23, 5), TestPosition(23, 12)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, Position(23, 5), newName)).join()

        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 11)), "$oldName: $newName"),
            TestTextEdit(TestRange(TestPosition(11, 13), TestPosition(11, 20)), newName),
            TestTextEdit(TestRange(TestPosition(18, 18), TestPosition(18, 25)), newName),
            TestTextEdit(TestRange(TestPosition(23, 5), TestPosition(23, 12)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename enum type renames enum where used as argument`(@TempDir tempDir: Path) {
        val oldName = "my_enum"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
    
                function f($oldName) = $oldName;
                
                enum $oldName {
                    foo,
                    bar
                }
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(4, 7)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionForRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(4, 5), TestPosition(4, 12)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 5), TestPosition(4, 12)), newName),
            TestTextEdit(TestRange(TestPosition(2, 11), TestPosition(2, 18)), "$oldName: $newName"),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename enum where used as argument renames argument without renaming type`(@TempDir tempDir: Path) {
        val oldName = "my_enum"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;

                function f($oldName) = $oldName;
                
                enum $oldName {
                    foo,
                    bar
                }  
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(2, 15)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionForRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(2, 11), TestPosition(2, 18)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()

        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 11), TestPosition(2, 18)), "$newName: $oldName"),
            TestTextEdit(TestRange(TestPosition(2, 22), TestPosition(2, 29)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename on local parameter referenced through enum argument, only renames argument and variable`(
        @TempDir tempDir: Path
    ) {
        val oldName = "my_enum"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;

                function f($oldName) = $oldName;
                
                enum $oldName {
                    foo,
                    bar
                }  
                """.trimIndent()
            )
        }
        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(2, 25)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionForRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(2, 11), TestPosition(2, 18)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()

        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 11), TestPosition(2, 18)), "$newName: $oldName"),
            TestTextEdit(TestRange(TestPosition(2, 22), TestPosition(2, 29)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename struct with attribute with unqualified name`(@TempDir tempDir: Path) {
        val oldName = "another_struct"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                
                struct my_struct1 {
                    a_property: text;
                    $oldName;
                }
                
                function f1(my_struct1) = my_struct1.$oldName.another_property;
                
                struct my_struct2 {
                    a_property: text;
                    $oldName: $oldName;
                }
                
                function f2(my_struct2) = my_struct2.$oldName.another_property;
                
                struct my_struct3 {
                    a_property: text;
                    ref_property: $oldName;
                }
                
                function f3(my_struct3) = my_struct3.ref_property.another_property;
                                
                struct $oldName {
                    another_property: text;
                }
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, Position(23, 9))).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(23, 7), TestPosition(23, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, Position(23, 9), newName)).join()

        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 18)), "$oldName: $newName"),
            TestTextEdit(TestRange(TestPosition(11, 20), TestPosition(11, 34)), newName),
            TestTextEdit(TestRange(TestPosition(18, 18), TestPosition(18, 32)), newName),
            TestTextEdit(TestRange(TestPosition(23, 7), TestPosition(23, 21)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename struct on reference type shortcut only rename reference`(@TempDir tempDir: Path) {
        val oldName = "another_struct"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
            
                struct my_struct1 {
                    a_property: text;
                    $oldName;
                }
                
                function f1(my_struct1) = my_struct1.$oldName.another_property;
                
                struct my_struct2 {
                    a_property: text;
                    $oldName: $oldName;
                }
                
                function f2(my_struct2) = my_struct2.$oldName.another_property;
                
                struct my_struct3 {
                    a_property: text;
                    ref_property: $oldName;
                }
                
                function f3(my_struct3) = my_struct3.ref_property.another_property;
                                
                struct $oldName {
                    another_property: text;
                }                    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(4, 12)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionForRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(4, 4), TestPosition(4, 18)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()

        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 18)), "$newName: $oldName"),
            TestTextEdit(TestRange(TestPosition(7, 37), TestPosition(7, 51)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename struct on explicit type reference renames type`(@TempDir tempDir: Path) {
        val oldName = "another_struct"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                
                struct my_struct1 {
                    a_property: text;
                    $oldName;
                }
                
                function f1(my_struct1) = my_struct1.$oldName.another_property;
                
                struct my_struct2 {
                    a_property: text;
                    $oldName: $oldName;
                }
                
                function f2(my_struct2) = my_struct2.$oldName.another_property;
                
                struct my_struct3 {
                    a_property: text;
                    ref_property: $oldName;
                }
                
                function f3(my_struct3) = my_struct3.ref_property.another_property;
                                
                struct $oldName {
                    another_property: text;
                }                    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(11, 25)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionForRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(23, 7), TestPosition(23, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 18)), "$oldName: $newName"),
            TestTextEdit(TestRange(TestPosition(11, 20), TestPosition(11, 34)), newName),
            TestTextEdit(TestRange(TestPosition(18, 18), TestPosition(18, 32)), newName),
            TestTextEdit(TestRange(TestPosition(23, 7), TestPosition(23, 21)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename entity with attribute with unqualified name`(@TempDir tempDir: Path) {
        val oldName = "another_entity"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                
                entity my_entity1 {
                    a_property: text;
                    $oldName;
                }
                
                function q1() = my_entity1 @* { .$oldName.another_property == "test" };
                
                entity my_entity2 {
                    a_property: text;
                    $oldName: $oldName;
                }
                
                function q2() = my_entity2 @* { .$oldName.another_property == "test" };
                
                entity my_entity3 {
                    a_property: text;
                    ref_property: $oldName;
                }
                
                function q3() = my_entity3 @* { .ref_property.another_property == "test" };
                
                entity $oldName {
                    another_property: text;
                }
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, Position(23, 9))).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(23, 7), TestPosition(23, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, Position(23, 9), newName)).join()

        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 18)), "$oldName: $newName"),
            TestTextEdit(TestRange(TestPosition(11, 20), TestPosition(11, 34)), newName),
            TestTextEdit(TestRange(TestPosition(18, 18), TestPosition(18, 32)), newName),
            TestTextEdit(TestRange(TestPosition(23, 7), TestPosition(23, 21)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename entity with attribute with qualified name`(@TempDir tempDir: Path) {
        val testFilePath = "entity_module.rell"
        val oldName = "another_entity"

        val testDataBuilder = testData(tempDir) {
            addFile(
                testFilePath,
                """
                module;

                entity $oldName {
                    another_property: text;
                }
                """.trimIndent()
            )
            addMainFile(
                """
                module;
                import entity_module;
                
                entity my_entity1 {
                    a_property: text;
                    entity_module.$oldName;
                }
                
                function q1() = my_entity1 @* { .$oldName.another_property == "test" };
                
                entity my_entity2 {
                    a_property: text;
                    $oldName: entity_module.$oldName;
                }
                
                function q2() = my_entity2 @* { .$oldName.another_property == "test" };
                
                entity my_entity3 {
                    a_property: text;
                    ref_property: entity_module.$oldName;
                }
                
                function q3() = my_entity3 @* { .ref_property.another_property == "test" };
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.sourceFile(testFilePath))
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, Position(2, 10))).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(2, 7), TestPosition(2, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val affectedFileUri = testDataBuilder.mainFile.toFileUri()
        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, Position(2, 10), newName)).join()

        assertThat(result.changes.keys).containsOnly(affectedFileUri, fileOfRenamingEvent)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 7), TestPosition(2, 21)), newName),
        )
        assertThat(result.changes[affectedFileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(
                TestRange(TestPosition(5, 4), TestPosition(5, 32)),
                "$oldName: entity_module.$newName"
            ),
            TestTextEdit(TestRange(TestPosition(12, 34), TestPosition(12, 48)), newName),
            TestTextEdit(TestRange(TestPosition(19, 32), TestPosition(19, 46)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename with import alias`(@TempDir tempDir: Path) {
        val affectedFilePath = "foobar_module/module.rell"
        val oldName = "foo_entity"

        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                
                import a: foobar_module.{$oldName};   
                """.trimIndent()
            )
            addFile(
                affectedFilePath,
                """
                module;

                entity $oldName {
                    name;
                }    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(2, 30)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, Position(2, 30))).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(2, 7), TestPosition(2, 17)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val affectedFileUri = testDataBuilder.sourceFile(affectedFilePath).toFileUri()
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()

        assertThat(result.changes.keys).containsOnly(fileOfRenamingEvent, affectedFileUri)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 25), TestPosition(2, 35)), newName),
        )
        assertThat(result.changes[affectedFileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 7), TestPosition(2, 17)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename on reference type shortcut`(@TempDir tempDir: Path) {
        val oldName = "foo_entity"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                
                entity $oldName {}
                
                entity bar_entity {
                    $oldName;
                }
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(5, 7)

        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionForRenaming)).join()
        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(5, 4), TestPosition(5, 14)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()

        assertThat(result.changes[fileOfRenamingEvent]!![0].newText).isEqualTo("$newName: $oldName")
        assertThat(result.changes.keys).containsOnly(fileOfRenamingEvent)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(
                TestRange(
                    TestPosition(5, 4),
                    TestPosition(5, 14)
                ),
                "$newName: $oldName"
            ),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename on entity attribute reference usage with type shortcut as field name`(@TempDir tempDir: Path) {
        val oldName = "another_entity"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
    
                entity my_entity {
                    a_property: text;
                    $oldName;
                }
                
                entity $oldName {
                    another_property: text;
                }
                
                function q() = my_entity @* { .$oldName.another_property == "test" };                    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionFoRenaming = Position(11, 39)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionFoRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(4, 4), TestPosition(4, 18)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionFoRenaming, newName)).join()

        assertThat(result.changes.keys).containsOnly(fileOfRenamingEvent)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 4), TestPosition(4, 18)), "$newName: $oldName"),
            TestTextEdit(TestRange(TestPosition(11, 31), TestPosition(11, 45)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename on attribute reference usage with explicit name`(@TempDir tempDir: Path) {
        val oldName = "my_entity3"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
    
                entity my_entity {
                    a_property: text;
                    field: $oldName;
                }
                
                entity my_entity2 {
                    a_property: text;
                    $oldName;
                }
                
                entity $oldName {
                    another_property: text;
                }
                
                function q() = my_entity2 @* { .$oldName.another_property == "test" };                    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionFoRenaming = Position(16, 35)
        val newName = "something_else"
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionFoRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(9, 4), TestPosition(9, 14)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val result = server.rename(renameParams(fileOfRenamingEvent, positionFoRenaming, newName)).join()

        assertThat(result.changes.keys).containsOnly(fileOfRenamingEvent)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(9, 4), TestPosition(9, 14)), "$newName: $oldName"),
            TestTextEdit(TestRange(TestPosition(16, 32), TestPosition(16, 42)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename on explicit type reference`(@TempDir tempDir: Path) {
        val oldName = "another_entity"
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
    
                entity my_entity {
                    a_property: text;
                    field: $oldName;
                }
                
                entity $oldName {
                    another_property: text;
                }
                
                function q() = my_entity @* { .field.another_property == "test" };                    
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionFoRenaming = Position(4, 15)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionFoRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(7, 7), TestPosition(7, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionFoRenaming, newName)).join()

        assertThat(result.changes.keys).containsOnly(fileOfRenamingEvent)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(4, 11), TestPosition(4, 25)), newName),
            TestTextEdit(TestRange(TestPosition(7, 7), TestPosition(7, 21)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename on entity attribute reference type shortcut with qualified name`(@TempDir tempDir: Path) {
        val dependentFileName = "entity_module.rell"
        val oldName = "another_entity"

        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                import entity_module;
                
                entity my_entity1 {
                    a_property: text;
                    entity_module.$oldName;
                }
                
                function q1() = my_entity1 @* { .$oldName.another_property == "test" };
                
                entity my_entity2 {
                    a_property: text;
                    $oldName: entity_module.$oldName;
                }
                
                function q2() = my_entity2 @* { .$oldName.another_property == "test" };
                
                entity my_entity3 {
                    a_property: text;
                    ref_property: entity_module.$oldName;
                }
                
                function q3() = my_entity3 @* { .ref_property.another_property == "test" };
                """.trimIndent()
            )
            addFile(
                dependentFileName,
                """
                module;

                entity $oldName {
                    another_property: text;
                }
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(5, 25)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionForRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(5, 18), TestPosition(5, 32)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()

        assertThat(result.changes.keys).containsOnly(fileOfRenamingEvent)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(
                TestRange(TestPosition(5, 4), TestPosition(5, 32)),
                "$newName: entity_module.$oldName"
            ),
            TestTextEdit(TestRange(TestPosition(8, 33), TestPosition(8, 47)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    @Test
    fun `Rename on entity explicit type attribute reference with qualified name`(@TempDir tempDir: Path) {
        val affectedFilePath = "entity_module.rell"
        val oldName = "another_entity"

        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                import entity_module;
                
                entity my_entity1 {
                    a_property: text;
                    entity_module.$oldName;
                }
                
                function q1() = my_entity1 @* { .$oldName.another_property == "test" };
                
                entity my_entity2 {
                    a_property: text;
                    $oldName: entity_module.$oldName;
                }
                
                function q2() = my_entity2 @* { .$oldName.another_property == "test" };
                
                entity my_entity3 {
                    a_property: text;
                    ref_property: entity_module.$oldName;
                }
                
                function q3() = my_entity3 @* { .ref_property.another_property == "test" };
                """.trimIndent()
            )
            addFile(
                affectedFilePath,
                """
                module;

                entity $oldName {
                    another_property: text;
                }
                """.trimIndent()
            )
        }

        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val fileOfRenamingEvent = openFile(testDataBuilder.mainFile)
        val positionForRenaming = Position(12, 42)
        val prepareResult = server.prepareRename(prepareRenameParams(fileOfRenamingEvent, positionForRenaming)).join()

        assertThat(prepareResult.first).isNull()
        assertThat(TestPrepareRenameResult(prepareResult.second)).isEqualTo(
            TestPrepareRenameResult(
                TestRange(TestPosition(2, 7), TestPosition(2, 21)),
                oldName
            )
        )
        assertThat(prepareResult.third).isNull()

        val newName = "something_else"
        val affectedFileUri = openFile(testDataBuilder.sourceFile(affectedFilePath))
        val result = server.rename(renameParams(fileOfRenamingEvent, positionForRenaming, newName)).join()

        assertThat(result.changes.keys).containsOnly(fileOfRenamingEvent, affectedFileUri)
        assertThat(result.changes[fileOfRenamingEvent]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(
                TestRange(TestPosition(5, 4), TestPosition(5, 32)),
                "$oldName: entity_module.$newName"
            ),
            TestTextEdit(TestRange(TestPosition(12, 34), TestPosition(12, 48)), newName),
            TestTextEdit(TestRange(TestPosition(19, 32), TestPosition(19, 46)), newName),
        )
        assertThat(result.changes[affectedFileUri]!!.map { TestTextEdit(it) }).containsExactlyInAnyOrder(
            TestTextEdit(TestRange(TestPosition(2, 7), TestPosition(2, 21)), newName),
        )

        applyChanges(result.changes)
        compileRell(testDataBuilder.sourceFolder)
    }

    private fun openFile(file: File): String {
        val fileOfRenamingEvent = file.toURI().toString()
        val textDocumentItem = TextDocumentItem(fileOfRenamingEvent, "rell", 1, file.readText())
        val didOpenParam = DidOpenTextDocumentParams(textDocumentItem)
        server.didOpen(didOpenParam)
        return fileOfRenamingEvent
    }

    private fun prepareRenameParams(fileOfRenamingEvent: String, position: Position) =
        PrepareRenameParams(TextDocumentIdentifier(fileOfRenamingEvent), position)

    private fun renameParams(fileOfRenamingEvent: String, position: Position, newName: String) =
        RenameParams(TextDocumentIdentifier(fileOfRenamingEvent), position, newName)

    private fun applyChanges(changes: Map<String, List<TextEdit>>) {
        changes.forEach { (fileOfRenamingEvent, textEdits) ->
            val uri = URI(fileOfRenamingEvent)
            val document = workspaceManager.getOpenDocument(uri) ?: Document(uri, 1, uri.toPath().readText())
            val changedDocument = document.applyTextDocumentChanges(
                textEdits.map {
                    TextDocumentContentChangeEvent(
                        it.range,
                        it.newText
                    )
                }
            )
            uri.toPath().writeText(changedDocument.content)
        }
    }

    private fun compileRell(rellSourceDir: File) {
        RellApiCompile.compileApp(
            RellApiCompile.Config.Builder()
                .mountConflictError(false)
                .moduleArgsMissingError(false)
                .quiet(false)
                .build(),
            rellSourceDir,
            appModules = null
        )
    }

    private fun File.toFileUri() = this.toURI().toString()
}
