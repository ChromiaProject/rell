/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.editing

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.TestPosition
import net.postchain.rell.toolbox.lsp.TestRange
import net.postchain.rell.toolbox.lsp.TestTextEdit
import net.postchain.rell.toolbox.testing.testData
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.eclipse.lsp4j.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

/**
 * Covers the `refactor.rewrite` body-conversion pair. The conversion must never change a
 * definition's (possibly inferred) return type: a unit expression body becomes `{ expr; }`
 * rather than the illegal `{ return expr; }`, and a `{ call(); }` block converts only when the
 * call is known to return unit.
 */
class BodyConversionServiceTest {
    @TempDir
    private lateinit var tempDir: File
    private lateinit var mainFileUri: URI
    private lateinit var indexer: WorkspaceIndexer

    @Test
    fun `should convert a single-return block to an expression body`() {
        val actions = actionsAt(
            """
            module;
            function f(x: integer): integer {
                return x + 1;
            }
            """.trimIndent(),
            line = 2,
        )

        val action = actions.single { it.title == BodyConversionService.TO_EXPRESSION_TITLE }
        assertThat(action.kind).isEqualTo(CodeActionKind.RefactorRewrite)
        assertThat(action.isPreferred).isEqualTo(false)
        assertThat(editsOf(action)).isEqualTo(
            listOf(TestTextEdit(TestRange(TestPosition(1, 32), TestPosition(3, 1)), "= x + 1;")),
        )
    }

    @Test
    fun `should convert a non-unit expression body to a return block`() {
        val actions = actionsAt(
            """
            module;
            function f(x: integer) = x + 1;
            """.trimIndent(),
            line = 1,
        )

        val action = actions.single { it.title == BodyConversionService.TO_BLOCK_TITLE }
        assertThat(action.kind).isEqualTo(CodeActionKind.RefactorRewrite)

        assertThat(editsOf(action)).isEqualTo(
            listOf(
                TestTextEdit(
                    TestRange(TestPosition(1, 23), TestPosition(1, 31)),
                    "{\n    return x + 1;\n}",
                ),
            ),
        )
    }

    @Test
    fun `should convert a unit expression body to a block without a return`() {
        val code = """
            module;
            function b() {}
            function a() = b();
        """.trimIndent()

        val actions = actionsAt(code, line = 2)

        val action = actions.single { it.title == BodyConversionService.TO_BLOCK_TITLE }

        assertThat(editsOf(action)).isEqualTo(
            listOf(TestTextEdit(TestRange(TestPosition(2, 13), TestPosition(2, 19)), "{\n    b();\n}")),
        )

        assertConvertedCompiles(code, action)
    }

    @Test
    fun `should convert an explicitly unit-typed expression body to a block without a return`() {
        val code = """
            module;
            function b() {}
            function a(): unit = b();
        """.trimIndent()

        val actions = actionsAt(code, line = 2)

        val action = actions.single { it.title == BodyConversionService.TO_BLOCK_TITLE }

        assertThat(editsOf(action)).isEqualTo(
            listOf(TestTextEdit(TestRange(TestPosition(2, 19), TestPosition(2, 25)), "{\n    b();\n}")),
        )

        assertConvertedCompiles(code, action)
    }

    @Test
    fun `should convert a single unit-call block to an expression body`() {
        val code = """
            module;
            function b() {}
            function a() {
                b();
            }
        """.trimIndent()

        val actions = actionsAt(code, line = 3)

        val action = actions.single { it.title == BodyConversionService.TO_EXPRESSION_TITLE }

        assertThat(editsOf(action)).isEqualTo(
            listOf(TestTextEdit(TestRange(TestPosition(2, 13), TestPosition(4, 1)), "= b();")),
        )

        assertConvertedCompiles(code, action)
    }

    @Test
    fun `should not convert a block whose call returns a value`() {
        // `{ b(); }` discards the integer, but `= b();` would change the inferred
        // signature of `a` from unit to integer.
        val titles = actionsAt(
            """
            module;
            function b(): integer = 1;
            function a() {
                b();
            }
            """.trimIndent(),
            line = 3,
        ).map { it.title }

        assertThat(titles).doesNotContain(BodyConversionService.TO_EXPRESSION_TITLE)
    }

    @Test
    fun `should not convert a block containing a comment outside the expression`() {
        val titles = actionsAt(
            """
            module;
            function f(): integer {
                // explains the constant
                return 1;
            }
            """.trimIndent(),
            line = 3,
        ).map { it.title }

        assertThat(titles).doesNotContain(BodyConversionService.TO_EXPRESSION_TITLE)
    }

    @Test
    fun `should convert a query in both directions`() {
        val toBlock = actionsAt(
            """
            module;
            query q() = 1;
            """.trimIndent(),
            line = 1,
        ).single { it.title == BodyConversionService.TO_BLOCK_TITLE }

        // A query must return a value, so its block form always uses `return`.
        assertThat(editsOf(toBlock)).isEqualTo(
            listOf(TestTextEdit(TestRange(TestPosition(1, 10), TestPosition(1, 14)), "{\n    return 1;\n}")),
        )

        val toExpression = actionsAt(
            """
            module;
            query q() {
                return 1;
            }
            """.trimIndent(),
            line = 2,
        ).single { it.title == BodyConversionService.TO_EXPRESSION_TITLE }
        assertThat(editsOf(toExpression)).isEqualTo(
            listOf(TestTextEdit(TestRange(TestPosition(1, 10), TestPosition(3, 1)), "= 1;")),
        )
    }

    @Test
    fun `should indent the block body relative to a nested definition`() {
        val actions = actionsAt(
            """
            module;
            namespace ns {
                function f(): integer = 1;
            }
            """.trimIndent(),
            line = 2,
            character = 8,
        )

        val action = actions.single { it.title == BodyConversionService.TO_BLOCK_TITLE }

        assertThat(editsOf(action)).isEqualTo(
            listOf(
                TestTextEdit(
                    TestRange(TestPosition(2, 26), TestPosition(2, 30)),
                    "{\n        return 1;\n    }",
                ),
            ),
        )
    }

    @Test
    fun `should offer nothing for multi-statement, empty, bare-return, and assignment blocks`() {
        val titles = actionsAt(
            """
            module;
            function multi(x: integer): integer {
                val y = x + 1;
                return y;
            }
            """.trimIndent(),
            line = 2,
        ).map { it.title } + actionsAt(
            """
            module;
            function empty() {}
            """.trimIndent(),
            line = 1,
        ).map { it.title } + actionsAt(
            """
            module;
            function bare() {
                return;
            }
            """.trimIndent(),
            line = 2,
        ).map { it.title } + actionsAt(
            """
            module;
            struct s { mutable x: integer; }
            function assign(v: s) {
                v.x = 1;
            }
            """.trimIndent(),
            line = 3,
        ).map { it.title }

        assertThat(titles).doesNotContain(BodyConversionService.TO_EXPRESSION_TITLE)
        assertThat(titles).doesNotContain(BodyConversionService.TO_BLOCK_TITLE)
    }

    @Test
    fun `should offer nothing for an operation or outside any definition`() {
        val code = """
            module;
            operation o(x: integer) {
                require(x > 0);
            }
        """.trimIndent()

        val inOperation = actionsAt(code, line = 2).map { it.title }
        val outside = actionsAt(code, line = 0).map { it.title }

        assertThat(inOperation + outside).doesNotContain(BodyConversionService.TO_EXPRESSION_TITLE)
        assertThat(inOperation + outside).doesNotContain(BodyConversionService.TO_BLOCK_TITLE)
    }

    @Test
    fun `should respect the kind filter`() {
        val code = """
            module;
            function f(): integer {
                return 1;
            }
        """.trimIndent()

        val refactorOnly = actionsAt(code, line = 2, only = listOf("refactor")).map { it.title }
        assertThat(refactorOnly).contains(BodyConversionService.TO_EXPRESSION_TITLE)

        val quickFixOnly = actionsAt(code, line = 2, only = listOf("quickfix")).map { it.title }
        assertThat(quickFixOnly).doesNotContain(BodyConversionService.TO_EXPRESSION_TITLE)
    }

    @Test
    fun `should convert a returned if-expression in both directions`() {
        val blockCode = """
            module;
            function f(x: integer): integer {
                return if (x > 0) 1 else 2;
            }
        """.trimIndent()
        val toExpression = actionsAt(blockCode, line = 2)
            .single { it.title == BodyConversionService.TO_EXPRESSION_TITLE }
        assertThat(editsOf(toExpression)).isEqualTo(
            listOf(
                TestTextEdit(
                    TestRange(TestPosition(1, 32), TestPosition(3, 1)),
                    "= if (x > 0) 1 else 2;",
                ),
            ),
        )
        assertConvertedCompiles(blockCode, toExpression)

        val expressionCode = """
            module;
            function f(x: integer) = if (x > 0) 1 else 2;
        """.trimIndent()
        val toBlock = actionsAt(expressionCode, line = 1)
            .single { it.title == BodyConversionService.TO_BLOCK_TITLE }
        assertThat(editsOf(toBlock)).isEqualTo(
            listOf(
                TestTextEdit(
                    TestRange(TestPosition(1, 23), TestPosition(1, 45)),
                    "{\n    return if (x > 0) 1 else 2;\n}",
                ),
            ),
        )
        assertConvertedCompiles(expressionCode, toBlock)
    }

    @Test
    fun `should convert a returned multi-line when-expression preserving its layout`() {
        val code = """
            module;
            function f(x: integer): integer {
                return when (x) {
                    0 -> 1;
                    else -> 2
                };
            }
        """.trimIndent()

        val actions = actionsAt(code, line = 3)

        val action = actions.single { it.title == BodyConversionService.TO_EXPRESSION_TITLE }
        assertThat(editsOf(action)).isEqualTo(
            listOf(
                TestTextEdit(
                    TestRange(TestPosition(1, 32), TestPosition(6, 1)),
                    "= when (x) {\n        0 -> 1;\n        else -> 2\n    };",
                ),
            ),
        )
        assertConvertedCompiles(code, action)
    }

    @Test
    fun `should convert a body containing an embedded jump expression in both directions`() {
        // `x ?: return 0` is legal exactly where the return statement would be — including an
        // expression body — so the conversion preserves it verbatim.
        val expressionCode = """
            module;
            function f(x: integer?): integer = x ?: return 0;
        """.trimIndent()
        val toBlock = actionsAt(expressionCode, line = 1)
            .single { it.title == BodyConversionService.TO_BLOCK_TITLE }
        assertThat(editsOf(toBlock)).isEqualTo(
            listOf(
                TestTextEdit(
                    TestRange(TestPosition(1, 33), TestPosition(1, 49)),
                    "{\n    return x ?: return 0;\n}",
                ),
            ),
        )
        assertConvertedCompiles(expressionCode, toBlock)

        val blockCode = """
            module;
            function f(x: integer?): integer {
                return x ?: return 0;
            }
        """.trimIndent()
        val toExpression = actionsAt(blockCode, line = 2)
            .single { it.title == BodyConversionService.TO_EXPRESSION_TITLE }
        assertThat(editsOf(toExpression)).isEqualTo(
            listOf(TestTextEdit(TestRange(TestPosition(1, 33), TestPosition(3, 1)), "= x ?: return 0;")),
        )
        assertConvertedCompiles(blockCode, toExpression)
    }

    @Test
    fun `should not convert a body that is a bare jump expression`() {
        // `= return 1;` <-> `{ return return 1; }` — both rewrites are absurd, so neither
        // direction is offered.
        val toBlockTitles = actionsAt(
            """
            module;
            function f(): integer = return 1;
            """.trimIndent(),
            line = 1,
        ).map { it.title }
        assertThat(toBlockTitles).doesNotContain(BodyConversionService.TO_BLOCK_TITLE)

        val toExpressionTitles = actionsAt(
            """
            module;
            function f(): integer {
                return return 1;
            }
            """.trimIndent(),
            line = 2,
        ).map { it.title }
        assertThat(toExpressionTitles).doesNotContain(BodyConversionService.TO_EXPRESSION_TITLE)
    }

    private fun actionsAt(
        @Language("Rell") code: String, line: Int, character: Int = 0, only: List<String>? = null
    ): List<CodeAction> {
        val workspaceFolder = testData(tempDir) { addMainFile(code) }.workspaceFolder
        mainFileUri = workspaceFolder.resolve("src/main.rell").toURI()

        indexer = WorkspaceIndexer(
            workspaceFolder.toURI(),
            RellLinter(),
            testLinterOptions {},
            FormattingStyleLinter(),
            FormatterOptions(),
        )

        indexer.initialFileIndexBuild()
        val range = Range(Position(line, character), Position(line, character))
        return CodeActionService.getCodeActions(mainFileUri, range, indexer, only).map { it.right }
    }

    private fun editsOf(action: CodeAction): List<TestTextEdit> =
        action.edit.changes[mainFileUri.toString()].orEmpty().map { TestTextEdit(it) }

    /** Applies the action's edit to the original source and asserts the result still compiles. */
    private fun assertConvertedCompiles(code: String, action: CodeAction) {
        val edit = action.edit.changes[mainFileUri.toString()]!!.single()
        val workspaceFolder = testData(tempDir) { addMainFile(applyEdit(code, edit)) }.workspaceFolder

        val checkIndexer = WorkspaceIndexer(
            workspaceFolder.toURI(),
            RellLinter(),
            testLinterOptions {},
            FormattingStyleLinter(),
            FormatterOptions(),
        )

        checkIndexer.initialFileIndexBuild()
        val resource = checkIndexer.getResource(workspaceFolder.resolve("src/main.rell").toURI())!!
        assertThat(resource.syntaxErrors.map { it.message }).isEqualTo(listOf())

        assertThat(resource.semanticErrors.filter { it.type == C_MessageType.ERROR }.map { it.code })
            .isEqualTo(listOf())
    }

    private fun applyEdit(code: String, edit: TextEdit): String {
        val lines = code.split("\n")
        val start = lines.take(edit.range.start.line).sumOf { it.length + 1 } + edit.range.start.character
        val end = lines.take(edit.range.end.line).sumOf { it.length + 1 } + edit.range.end.character
        return code.substring(0, start) + edit.newText + code.substring(end)
    }
}
