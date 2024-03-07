package net.postchain.rell.toolbox.core.tokens

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import assertk.assertions.isEqualTo
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.core.indexer.RellResourceFactory
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.io.path.createDirectory

class RellSemanticTokensManagerTest {

    @Test
    fun `Correct semantic tokens returned`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
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
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser())
        val resource = resourceFactory.buildRellResource(rellFile.toURI(), fileMap)

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
        }.containsAll(
            mainFunctionSemanticToken,
            fooFunctionSemanticToken
        )
    }

    @Test
    fun `Most common object mappings are covered`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
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
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser())
        val resource = resourceFactory.buildRellResource(rellFile.toURI(), fileMap)

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
        val tokenTypes = RellSemanticTokensManager().getSemanticTokens(resource).map {it.tokenType}.distinct()
        tokenTypes.forEach {
            assertThat(mappingTypes).contains(it)
        }
    }

    @Test
    fun `test that all tokens are covered`() {
        assertThat(provideParameters().count().toInt()).isEqualTo(IdeSymbolKind.entries.size)
    }

    @ParameterizedTest
    @MethodSource("provideParameters")
    fun `test all token types mappings`(origin: IdeSymbolKind, target: RellTokenType) {
        assertThat(target).isEqualTo(tokenFromIdeKind(origin))
    }

    @Test
    fun `Most common value mappings are covered`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
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
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser())
        val resource = resourceFactory.buildRellResource(rellFile.toURI(), fileMap)

        val mappingTypes = listOf(
            RellTokenType.DEFAULT,
            RellTokenType.GLOBAL_CONSTANT,
            RellTokenType.TYPE,
            RellTokenType.FUNCTION,
            RellTokenType.LOCAL_VAR,
            RellTokenType.LOCAL_VAL,
        )
        val tokenTypes = RellSemanticTokensManager().getSemanticTokens(resource).map {it.tokenType}.distinct()

        tokenTypes.forEach {
            assertThat(mappingTypes).contains(it)
        }
    }

    @Test
    fun `Most common method mappings are covered`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
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
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser())
        val resource = resourceFactory.buildRellResource(rellFile.toURI(), fileMap)

        val mappingTypes = listOf(
            RellTokenType.DEFAULT,
            RellTokenType.FUNCTION,
            RellTokenType.FUNCTION_EXTENDABLE,
            RellTokenType.ANNOTATION,
            RellTokenType.OPERATION,
            RellTokenType.QUERY
        )
        val tokenTypes = RellSemanticTokensManager().getSemanticTokens(resource).map {it.tokenType}.distinct()
        tokenTypes.forEach {
            assertThat(mappingTypes).contains(it)
        }
    }

    @Test
    fun `Correct relative semantic tokens returned`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
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
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser())
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resource = resourceFactory.buildRellResource(rellFile.toURI(), fileMap)

        val relativeTokens = RellSemanticTokensManager().getRelativeSemanticTokens(resource)

        val expectedRelativeTokens = arrayOf(1, 9, 4, 20, 4194304, 4, 9, 3, 20, 4194304)
        assertThat(relativeTokens).containsExactly(*expectedRelativeTokens)
    }

    companion object {
        @JvmStatic
        fun provideParameters(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(IdeSymbolKind.DEF_IMPORT_ALIAS, RellTokenType.DEFAULT),
                Arguments.of(IdeSymbolKind.DEF_CONSTANT, RellTokenType.GLOBAL_CONSTANT),
                Arguments.of(IdeSymbolKind.DEF_ENTITY, RellTokenType.ENTITY),
                Arguments.of(IdeSymbolKind.DEF_ENUM, RellTokenType.ENUM),
                Arguments.of(IdeSymbolKind.DEF_FUNCTION_ABSTRACT, RellTokenType.FUNCTION_EXTENDABLE),
                Arguments.of(IdeSymbolKind.DEF_FUNCTION_EXTEND, RellTokenType.FUNCTION),
                Arguments.of(IdeSymbolKind.DEF_FUNCTION_EXTENDABLE, RellTokenType.FUNCTION_EXTENDABLE),
                Arguments.of(IdeSymbolKind.DEF_FUNCTION, RellTokenType.FUNCTION),
                Arguments.of(IdeSymbolKind.DEF_FUNCTION_SYSTEM, RellTokenType.FUNCTION),
                Arguments.of(IdeSymbolKind.DEF_IMPORT_MODULE, RellTokenType.MODULE),
                Arguments.of(IdeSymbolKind.DEF_NAMESPACE, RellTokenType.NAMESPACE),
                Arguments.of(IdeSymbolKind.DEF_OBJECT, RellTokenType.OBJECT),
                Arguments.of(IdeSymbolKind.DEF_OPERATION, RellTokenType.OPERATION),
                Arguments.of(IdeSymbolKind.DEF_QUERY, RellTokenType.QUERY),
                Arguments.of(IdeSymbolKind.DEF_STRUCT, RellTokenType.STRUCT),
                Arguments.of(IdeSymbolKind.DEF_TYPE, RellTokenType.TYPE),
                Arguments.of(IdeSymbolKind.EXPR_CALL_ARG, RellTokenType.NAMED_ARGUMENT),
                Arguments.of(IdeSymbolKind.EXPR_IMPORT_ALIAS, RellTokenType.MODULE),
                Arguments.of(IdeSymbolKind.LOC_AT_ALIAS, RellTokenType.AT_ALIAS),
                Arguments.of(IdeSymbolKind.LOC_PARAMETER, RellTokenType.LOCAL_VAL),
                Arguments.of(IdeSymbolKind.LOC_VAL, RellTokenType.LOCAL_VAL),
                Arguments.of(IdeSymbolKind.LOC_VAR, RellTokenType.LOCAL_VAR),
                Arguments.of(IdeSymbolKind.MEM_ENTITY_ATTR_INDEX, RellTokenType.ENTITY_ATTR_KEYINDEX_VAL),
                Arguments.of(IdeSymbolKind.MEM_ENTITY_ATTR_INDEX_VAR, RellTokenType.ENTITY_ATTR_KEYINDEX_VAR),
                Arguments.of(IdeSymbolKind.MEM_ENTITY_ATTR_KEY, RellTokenType.ENTITY_ATTR_KEYINDEX_VAL),
                Arguments.of(IdeSymbolKind.MEM_ENTITY_ATTR_KEY_VAR, RellTokenType.ENTITY_ATTR_KEYINDEX_VAR),
                Arguments.of(IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL, RellTokenType.ENTITY_ATTR_NORMAL_VAL),
                Arguments.of(IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL_VAR, RellTokenType.ENTITY_ATTR_NORMAL_VAR),
                Arguments.of(IdeSymbolKind.MEM_ENTITY_ATTR_ROWID, RellTokenType.ENTITY_ATTR_KEYINDEX_VAL),
                Arguments.of(IdeSymbolKind.MEM_ENUM_VALUE, RellTokenType.ENUM_VALUE),
                Arguments.of(IdeSymbolKind.MEM_STRUCT_ATTR, RellTokenType.STRUCT_ATTR_VAL),
                Arguments.of(IdeSymbolKind.MEM_STRUCT_ATTR_VAR, RellTokenType.STRUCT_ATTR_VAR),
                Arguments.of(IdeSymbolKind.MEM_SYS_PROPERTY, RellTokenType.DEFAULT),
                Arguments.of(IdeSymbolKind.MEM_TUPLE_ATTR, RellTokenType.TUPLE_ATTR),
                Arguments.of(IdeSymbolKind.MOD_ANNOTATION, RellTokenType.ANNOTATION),
                Arguments.of(IdeSymbolKind.MOD_ANNOTATION_LEGACY, RellTokenType.ANNOTATION),
                Arguments.of(IdeSymbolKind.UNKNOWN, RellTokenType.DEFAULT),
                Arguments.of(IdeSymbolKind.MEM_SYS_PROPERTY_PURE, RellTokenType.DEFAULT)
            )
        }
    }
}

