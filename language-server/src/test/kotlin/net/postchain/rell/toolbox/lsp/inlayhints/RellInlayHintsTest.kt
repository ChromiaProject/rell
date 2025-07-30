package net.postchain.rell.toolbox.lsp.inlayhints

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Test

internal class RellInlayHintsTest : WorkspaceManagerTestBase() {

    private val testFilePath = "test_inlay_hints.rell"

    @Test
    fun `Parameter inlay hints are provided for function calls when enabled`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                
                function test_function(param1: integer, param2: text) {
                    return param1;
                }
                
                function main() {
                    val result = test_function(42, "hello");
                }
                """.trimIndent()
            )
        }

        val config = RellInlayHintsConfig(
            isReturnTypesEnabled = false,
            isVariableTypesEnabled = false,
            isParameterNamesEnabled = true
        )
        inlayHintManager.updateConfig(config)

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())

        val range = Range(Position(7, 0), Position(7, 50))
        val hints = inlayHintManager.getInlayHints(testFile.toURI(), range)

        assertThat(hints).isNotEmpty()

        val parameterHints = hints.filter { it.kind == InlayHintKind.Parameter }
        assertThat(parameterHints).hasSize(2)

        val firstParamHint = parameterHints.find { it.label.left.contains("param1") }
        assertThat(firstParamHint).isNotNull()

        val secondParamHint = parameterHints.find { it.label.left.contains("param2") }
        assertThat(secondParamHint).isNotNull()
    }

    @Test
    fun `Type inlay hints are provided for variable declarations when enabled`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                
                function main() {
                    val my_integer = 42;
                    val my_string = "hello";
                    val my_boolean = true;
                }
                """.trimIndent()
            )
        }

        val config = RellInlayHintsConfig(
            isReturnTypesEnabled = false,
            isVariableTypesEnabled = true,
            isParameterNamesEnabled = false
        )
        inlayHintManager.updateConfig(config)

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())

        val range = Range(Position(3, 0), Position(6, 0))
        val hints = inlayHintManager.getInlayHints(testFile.toURI(), range)

        assertThat(hints).isNotEmpty()

        val typeHints = hints.filter { it.kind == InlayHintKind.Type }
        assertThat(typeHints).hasSize(3)

        val integerHint = typeHints.find { it.label.left.contains("integer") }
        assertThat(integerHint).isNotNull()

        val textHint = typeHints.find { it.label.left.contains("text") }
        assertThat(textHint).isNotNull()

        val booleanHint = typeHints.find { it.label.left.contains("boolean") }
        assertThat(booleanHint).isNotNull()
    }

    @Test
    fun `No type hints for variables with explicit types`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                
                function main() {
                    val my_integer: integer = 42;
                    val my_string: text = "hello";
                }
                """.trimIndent()
            )
        }

        val config = RellInlayHintsConfig(
            isReturnTypesEnabled = false,
            isVariableTypesEnabled = true,
            isParameterNamesEnabled = false
        )
        inlayHintManager.updateConfig(config)

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())

        val range = Range(Position(3, 0), Position(5, 0))
        val hints = inlayHintManager.getInlayHints(testFile.toURI(), range)

        val typeHints = hints.filter { it.kind == InlayHintKind.Type }
        assertThat(typeHints).hasSize(0)
    }

    @Test
    fun `No parameter hints for named arguments`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                
                function test_function(param1: integer, param2: text) {
                    return param1;
                }
                
                function main() {
                    val result = test_function(param1 = 42, param2 = "hello");
                }
                """.trimIndent()
            )
        }

        val config = RellInlayHintsConfig(
            isReturnTypesEnabled = false,
            isVariableTypesEnabled = false,
            isParameterNamesEnabled = true
        )
        inlayHintManager.updateConfig(config)

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())

        val range = Range(Position(7, 0), Position(7, 50))
        val hints = inlayHintManager.getInlayHints(testFile.toURI(), range)

        assertThat(hints).hasSize(0)
    }

    @Test
    fun `Inlay hints respect range parameter`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                
                function main() {
                    val my_integer = 42;
                    val my_string = "hello";
                }
                """.trimIndent()
            )
        }

        val config = RellInlayHintsConfig(
            isReturnTypesEnabled = false,
            isVariableTypesEnabled = true,
            isParameterNamesEnabled = false
        )
        inlayHintManager.updateConfig(config)

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())

        val limitedRange = Range(Position(3, 0), Position(3, 30))
        val hints = inlayHintManager.getInlayHints(testFile.toURI(), limitedRange)

        val typeHints = hints.filter { it.kind == InlayHintKind.Type }
        assertThat(typeHints).hasSize(1)

        val integerHint = typeHints.find { it.label.left.contains("integer") }
        assertThat(integerHint).isNotNull()
    }

    @Test
    fun `No parameter hints when argument name matches parameter name`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                
                function test_function(param1: integer, param2: text) {
                    return param1;
                }
                
                function main() {
                    val param1 = 42;
                    val different_name = "hello";
                    val result = test_function(param1, different_name);
                }
                """.trimIndent()
            )
        }

        val config = RellInlayHintsConfig(
            isReturnTypesEnabled = false,
            isVariableTypesEnabled = false,
            isParameterNamesEnabled = true
        )
        inlayHintManager.updateConfig(config)

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())

        val range = Range(Position(9, 0), Position(9, 60))
        val hints = inlayHintManager.getInlayHints(testFile.toURI(), range)

        val parameterHints = hints.filter { it.kind == InlayHintKind.Parameter }

        assertThat(parameterHints).hasSize(1)

        val param2Hint = parameterHints.find { it.label.left.contains("param2") }
        assertThat(param2Hint).isNotNull()

        val param1Hint = parameterHints.find { it.label.left.contains("param1") }
        assertThat(param1Hint).isNull()
    }

    @Test
    fun `No hints shown when all settings are disabled`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                
                function main() {
                    val my_integer = 42;
                    test_function(42, "hello");
                }
                
                function test_function(param1: integer, param2: text) {
                    return param1;
                }
                """.trimIndent()
            )
        }

        val config = RellInlayHintsConfig(
            isReturnTypesEnabled = false,
            isVariableTypesEnabled = false,
            isParameterNamesEnabled = false
        )
        inlayHintManager.updateConfig(config)

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())

        val range = Range(Position(0, 0), Position(10, 0))
        val hints = inlayHintManager.getInlayHints(testFile.toURI(), range)

        assertThat(hints).hasSize(0)
    }
}
