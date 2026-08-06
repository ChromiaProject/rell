/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.tokens

import assertk.assertThat
import assertk.assertions.*
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.utils.ide.IdeSymbolCategory
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.indexer.RellResourceFactory
import net.postchain.rell.toolbox.parser.AntlrRellParser
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

internal class RellSemanticTokensManagerTest {
    private val rellFile = "rell_file.rell"

    @Test
    fun `Correct semantic tokens returned`(@TempDir tempDir: File) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFile,
                """
                module;
                function main() {
                    return "main";
                }

                function foo() {
                    return "foo";
                }
                """.trimIndent()
            )
        }
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val rellFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        val resource = resourceFactory.buildRellResource(rellFileUri, fileMap)

        val tokens = RellSemanticTokensManager().getSemanticTokens(resource)

        val functionSymbolKind = RellTokenType.FUNCTION
        val mainFunctionSemanticToken = listOf(1, 9, 4, functionSymbolKind.tokenId, functionSymbolKind.modifiersAsList)
        val fooFunctionSemanticToken = listOf(5, 9, 3, functionSymbolKind.tokenId, functionSymbolKind.modifiersAsList)
        assertThat(tokens).extracting {
            listOf(
                it.line,
                it.col,
                it.len,
                it.tokenType.tokenId,
                it.tokenType.modifiersAsList
            )
        }.containsAtLeast(
            mainFunctionSemanticToken,
            fooFunctionSemanticToken
        )
    }

    @Test
    fun `Most common object mappings are covered`(@TempDir tempDir: File) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFile,
                """
                module;
                enum en {S,}
                object foo { n: integer = 123;}
                entity e {name;}
                struct str { x: integer; }
                """.trimIndent()
            )
        }
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val rellFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        val resource = resourceFactory.buildRellResource(rellFileUri, fileMap)

        val mappingTypes = listOf(
            RellTokenType.TYPE,
            RellTokenType.ENUM,
            RellTokenType.ENUM_VALUE,
            RellTokenType.ENTITY,
            RellTokenType.ENTITY_ATTR_NORMAL_VAL,
            RellTokenType.OBJECT,
            RellTokenType.STRUCT,
            RellTokenType.STRUCT_ATTR_VAL,
        )
        val tokenTypes = RellSemanticTokensManager().getSemanticTokens(resource).map { it.tokenType }.distinct()
        for (type in tokenTypes) {
            assertThat(mappingTypes).contains(type)
        }
    }

    @Test
    fun `test that all tokens are covered`() {
        assertThat(provideParameters().count().toInt()).isGreaterThanOrEqualTo(IdeSymbolKind.entries.size)
    }

    @ParameterizedTest
    @MethodSource("provideParameters")
    fun `test all token types mappings`(origin: IdeSymbolKind, target: RellTokenType, isCall: Boolean) {
        val defId = if (isCall) null else DUMMY_DEF_ID
        val info = IdeSymbolInfo.make(origin, defId, null, null)
        assertThat(tokenFromIdeSymbolInfo(info)).isEqualTo(target)
    }

    @Test
    fun `Type symbol with attribute def id maps to attribute token`() {
        fun typeInfo(defId: IdeSymbolId?) = IdeSymbolInfo.make(IdeSymbolKind.DEF_TYPE, defId, null, null)
        fun attrId(root: IdeSymbolCategory) =
            IdeSymbolId(root, "d").appendMember(IdeSymbolCategory.ATTRIBUTE, Name.of("name"))

        assertThat(tokenFromIdeSymbolInfo(typeInfo(attrId(IdeSymbolCategory.ENTITY))))
            .isEqualTo(RellTokenType.ENTITY_ATTR_NORMAL_VAL)
        assertThat(tokenFromIdeSymbolInfo(typeInfo(attrId(IdeSymbolCategory.OBJECT))))
            .isEqualTo(RellTokenType.ENTITY_ATTR_NORMAL_VAL)
        assertThat(tokenFromIdeSymbolInfo(typeInfo(attrId(IdeSymbolCategory.STRUCT))))
            .isEqualTo(RellTokenType.STRUCT_ATTR_VAL)
        // Non-attribute def ids (e.g. a function parameter's) keep the type token.
        val paramId = IdeSymbolId(IdeSymbolCategory.FUNCTION, "f")
            .appendMember(IdeSymbolCategory.PARAMETER, Name.of("name"))
        assertThat(tokenFromIdeSymbolInfo(typeInfo(paramId))).isEqualTo(RellTokenType.TYPE)
        assertThat(tokenFromIdeSymbolInfo(typeInfo(null))).isEqualTo(RellTokenType.TYPE)
    }

    @Test
    fun `Attribute declared by bare type name is an attribute token`(@TempDir tempDir: File) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFile,
                """
                module;
                entity e { name; }
                object o { mutable name = "World"; }
                struct s { text; }
                """.trimIndent()
            )
        }
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val rellFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        val resource = resourceFactory.buildRellResource(rellFileUri, fileMap)

        val tokens = RellSemanticTokensManager().getSemanticTokens(resource)

        assertThat(tokens).extracting { listOf(it.line, it.col, it.len, it.tokenType) }
            .containsAtLeast(
                listOf(1, 11, 4, RellTokenType.ENTITY_ATTR_NORMAL_VAL),
                listOf(2, 19, 4, RellTokenType.ENTITY_ATTR_NORMAL_VAL),
                listOf(3, 11, 4, RellTokenType.STRUCT_ATTR_VAL),
            )
    }

    @Test
    fun `Most common value mappings are covered`(@TempDir tempDir: File) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFile,
                """
                module;
                import x: xyz;

                val global: text =  "";
                function fun() {
                    var local_var = abs(11);
                    val local_val = "";
                }
                """.trimIndent()
            )
        }
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val rellFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        val resource = resourceFactory.buildRellResource(rellFileUri, fileMap)

        val mappingTypes = listOf(
            RellTokenType.DEFAULT,
            RellTokenType.MODULE,
            RellTokenType.GLOBAL_CONSTANT,
            RellTokenType.TYPE,
            RellTokenType.FUNCTION,
            RellTokenType.FUNCTION_CALL,
            RellTokenType.LOCAL_VAR,
            RellTokenType.LOCAL_VAL,
        )
        val tokenTypes = RellSemanticTokensManager().getSemanticTokens(resource).map { it.tokenType }.distinct()

        for (type in tokenTypes) {
            assertThat(mappingTypes).contains(type)
        }
    }

    @Test
    fun `Annotation token covers only the annotation name`(@TempDir tempDir: File) {
        val testDataBuilder = testData(tempDir) {
            addFile(rellFile, "@mount('ft4')\nmodule;\n")
        }
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val rellFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        val resource = resourceFactory.buildRellResource(rellFileUri, fileMap)

        val tokens = RellSemanticTokensManager().getSemanticTokens(resource)

        // The `@`, the parentheses and the string argument must keep their own highlighting;
        // only `mount` is the annotation.
        assertThat(tokens).extracting { listOf(it.line, it.col, it.len, it.tokenType) }
            .containsExactly(listOf(0, 1, 5, RellTokenType.ANNOTATION))
    }

    @Test
    fun `Most common method mappings are covered`(@TempDir tempDir: File) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFile,
                """
                module;
                operation op() {}
                query q() = "";

                @deprecated function f() = 123;
                abstract function abst();
                @extendable function f() = [1];
                @extend(lib.f) function h() = [2];
                function fun() {}
                """.trimIndent()
            )
        }
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val rellFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        val resource = resourceFactory.buildRellResource(rellFileUri, fileMap)

        val mappingTypes = listOf(
            RellTokenType.DEFAULT,
            RellTokenType.FUNCTION,
            RellTokenType.FUNCTION_EXTENDABLE,
            RellTokenType.ANNOTATION,
            RellTokenType.OPERATION,
            RellTokenType.QUERY
        )
        val tokenTypes = RellSemanticTokensManager().getSemanticTokens(resource).map { it.tokenType }.distinct()
        for (type in tokenTypes) {
            assertThat(mappingTypes).contains(type)
        }
    }

    @Test
    fun `Correct relative semantic tokens returned`(@TempDir tempDir: File) {
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFile,
                """
                module;
                function main() {
                    return "main";
                }

                function foo() {
                    return "foo";
                }
                """.trimIndent()
            )
        }
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser(), ChromiaModelProvider(null))
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val rellFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        val resource = resourceFactory.buildRellResource(rellFileUri, fileMap)

        val relativeTokens = RellSemanticTokensManager().getRelativeSemanticTokens(resource)

        val expectedRelativeTokens = arrayOf(1, 9, 4, 20, 4194304, 4, 9, 3, 20, 4194304)
        assertThat(relativeTokens).containsExactly(*expectedRelativeTokens)
    }

    companion object {
        @JvmStatic
        fun provideParameters(): Stream<Arguments> {
            return Stream.of(
                makeArguments(IdeSymbolKind.DEF_IMPORT_ALIAS, RellTokenType.MODULE),
                makeArguments(IdeSymbolKind.DEF_CONSTANT, RellTokenType.GLOBAL_CONSTANT),
                makeArguments(IdeSymbolKind.DEF_ENTITY, RellTokenType.ENTITY),
                makeArguments(IdeSymbolKind.DEF_ENUM, RellTokenType.ENUM),
                makeArguments(IdeSymbolKind.DEF_FUNCTION_ABSTRACT, RellTokenType.FUNCTION_EXTENDABLE),
                makeArguments(IdeSymbolKind.DEF_FUNCTION_EXTEND, RellTokenType.FUNCTION),
                makeArguments(IdeSymbolKind.DEF_FUNCTION_EXTENDABLE, RellTokenType.FUNCTION_EXTENDABLE),
                makeArguments(IdeSymbolKind.DEF_FUNCTION, RellTokenType.FUNCTION),
                makeArguments(IdeSymbolKind.DEF_FUNCTION, RellTokenType.FUNCTION_CALL, isCall = true),
                makeArguments(IdeSymbolKind.DEF_FUNCTION_SYSTEM, RellTokenType.FUNCTION_CALL),
                makeArguments(IdeSymbolKind.DEF_IMPORT_MODULE, RellTokenType.MODULE),
                makeArguments(IdeSymbolKind.DEF_NAMESPACE, RellTokenType.NAMESPACE),
                makeArguments(IdeSymbolKind.DEF_OBJECT, RellTokenType.OBJECT),
                makeArguments(IdeSymbolKind.DEF_OPERATION, RellTokenType.OPERATION),
                makeArguments(IdeSymbolKind.DEF_OPERATION, RellTokenType.OPERATION_CALL, isCall = true),
                makeArguments(IdeSymbolKind.DEF_QUERY, RellTokenType.QUERY),
                makeArguments(IdeSymbolKind.DEF_QUERY, RellTokenType.QUERY_CALL, isCall = true),
                makeArguments(IdeSymbolKind.DEF_STRUCT, RellTokenType.STRUCT),
                makeArguments(IdeSymbolKind.DEF_TYPE, RellTokenType.TYPE),
                makeArguments(IdeSymbolKind.EXPR_CALL_ARG, RellTokenType.NAMED_ARGUMENT),
                makeArguments(IdeSymbolKind.EXPR_IMPORT_ALIAS, RellTokenType.MODULE),
                makeArguments(IdeSymbolKind.LOC_AT_ALIAS, RellTokenType.AT_ALIAS),
                makeArguments(IdeSymbolKind.LOC_PARAMETER, RellTokenType.LOCAL_PARAMETER),
                makeArguments(IdeSymbolKind.LOC_VAL, RellTokenType.LOCAL_VAL),
                makeArguments(IdeSymbolKind.LOC_VAR, RellTokenType.LOCAL_VAR),
                makeArguments(IdeSymbolKind.MEM_ENTITY_ATTR_INDEX, RellTokenType.ENTITY_ATTR_KEYINDEX_VAL),
                makeArguments(IdeSymbolKind.MEM_ENTITY_ATTR_INDEX_VAR, RellTokenType.ENTITY_ATTR_KEYINDEX_VAR),
                makeArguments(IdeSymbolKind.MEM_ENTITY_ATTR_KEY, RellTokenType.ENTITY_ATTR_KEYINDEX_VAL),
                makeArguments(IdeSymbolKind.MEM_ENTITY_ATTR_KEY_VAR, RellTokenType.ENTITY_ATTR_KEYINDEX_VAR),
                makeArguments(IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL, RellTokenType.ENTITY_ATTR_NORMAL_VAL),
                makeArguments(IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL_VAR, RellTokenType.ENTITY_ATTR_NORMAL_VAR),
                makeArguments(IdeSymbolKind.MEM_ENTITY_ATTR_ROWID, RellTokenType.ENTITY_ATTR_KEYINDEX_VAL),
                makeArguments(IdeSymbolKind.MEM_ENUM_VALUE, RellTokenType.ENUM_VALUE),
                makeArguments(IdeSymbolKind.MEM_FUNCTION_SYSTEM, RellTokenType.MEMBER_FUNCTION_CALL),
                makeArguments(IdeSymbolKind.MEM_STRUCT_ATTR, RellTokenType.STRUCT_ATTR_VAL),
                makeArguments(IdeSymbolKind.MEM_STRUCT_ATTR_VAR, RellTokenType.STRUCT_ATTR_VAR),
                makeArguments(IdeSymbolKind.MEM_SYS_PROPERTY, RellTokenType.DEFAULT),
                makeArguments(IdeSymbolKind.MEM_TUPLE_ATTR, RellTokenType.TUPLE_ATTR),
                makeArguments(IdeSymbolKind.MOD_ANNOTATION, RellTokenType.ANNOTATION),
                makeArguments(IdeSymbolKind.MOD_ANNOTATION_LEGACY, RellTokenType.ANNOTATION),
                makeArguments(IdeSymbolKind.UNKNOWN, RellTokenType.DEFAULT),
                makeArguments(IdeSymbolKind.MEM_SYS_PROPERTY_PURE, RellTokenType.DEFAULT)
            )
        }

        fun makeArguments(origin: IdeSymbolKind, target: RellTokenType, isCall: Boolean = false): Arguments {
            return Arguments.of(origin, target, isCall)
        }

        val DUMMY_DEF_ID = IdeSymbolId(IdeSymbolCategory.FUNCTION, "dummy")
    }
}
