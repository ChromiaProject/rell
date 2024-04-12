package com.chromia.rell.dokka.modules

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.chromia.rell.dokka.SingleFileRellDokkaPluginTest
import org.junit.jupiter.api.Test
import utils.TestOutputWriterPlugin

class NamespaceModuleTest: SingleFileRellDokkaPluginTest() {

    @Test
    fun `Same named queries in different namespaces are placed in correct package`() {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)
        singleFileTestInline("""
            namespace a {
                query test() = "";
            }
            namespace b {
                query test() = "";
            }
        """.trimIndent(), listOf(writerPlugin)) {
           renderingStage = { _,_ ->
               assertThat(writerPlugin.writer.contents.containsKey("test-dapp/main.a/test.html")).isTrue()
               assertThat(writerPlugin.writer.contents.containsKey("test-dapp/main.b/test.html")).isTrue()
               assertThat(writerPlugin.writer.contents.containsKey("test-dapp/main/test.html")).isFalse()
           }
        }
    }

    @Test
    fun `Extension functions in different namespaces are placed in correct package`() {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)
        singleFileTestInline("""
            namespace a {
                @extend(b.extendable_fun)
                function extension_fun() = "";
            }
            namespace b {
                @extendable
                function extendable_fun(): text?;
            }
        """.trimIndent(), listOf(writerPlugin)) {
            renderingStage = { _,_ ->
                assertThat(writerPlugin.writer.contents.containsKey("test-dapp/main.a/extension_fun.html")).isTrue()
                assertThat(writerPlugin.writer.contents.containsKey("test-dapp/main.b/extendable_fun.html")).isTrue()
            }
        }
    }

    @Test
    fun `Namespaces are treated as package`() {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)
        singleFileTestInline("""
            namespace a {
                query q_test() = "";
                operation op_test() {}
                function f_test() = "";
                entity my_entity {name;}
                object my_object {name = "";}
                struct my_stuct {name;}
                enum my_enum {A}
                val my_const = "";
            }
     
        """.trimIndent(), listOf(writerPlugin)) {
            renderingStage = { _,_ ->
                val nameSpacePages = writerPlugin.writer.contents.keys.filter { it.startsWith("test-dapp/main.a") }
                assertThat(nameSpacePages.size).isEqualTo( 8 /*Definition Pages */ + 1 /*Package Index Page*/ + 4 /*Class Like Field Pages*/)
            }
        }
    }
}
