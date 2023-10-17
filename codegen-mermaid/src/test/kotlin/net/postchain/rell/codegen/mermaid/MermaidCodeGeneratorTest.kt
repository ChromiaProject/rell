package net.postchain.rell.codegen.mermaid

import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.hasSize
import com.github.dockerjava.api.command.WaitContainerResultCallback
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.MermaidCodeGeneratorConfig
import net.postchain.rell.codegen.MermaidDocumentFactory
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.CachedRellCliEnv
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.WaitingConsumer
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitStrategy
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.io.File
import java.nio.file.Files
import java.time.Duration
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

@Testcontainers
internal class MermaidCodeGeneratorTest {

    private val rellCliEnv = CachedRellCliEnv(RellCliEnv.DEFAULT, true, true)
    private val config = object : MermaidCodeGeneratorConfig {
        override fun mdx() = false
    }
    private val generator = CodeGenerator(MermaidDocumentFactory(config), config, rellCliEnv)

    private fun generateSvg(rellPath: String, vararg baseModule: String): Pair<List<DocumentSection>, Map<String, Document>> {
        val sections = generator.createSections(
                File(this::class.java.getResource(rellPath)!!.toURI()),
                baseModule.asList())
        val documents = generator.constructDocuments(sections)
        val target = Files.createTempDirectory("rell-codegen")
        DocumentSaver(target.toFile()).saveDocuments(documents)

        val containerPath = "/data"
        GenericContainer("ghcr.io/mermaid-js/mermaid-cli/mermaid-cli")
                .withFileSystemBind(target.absolutePathString(), containerPath)
                .withCommand("-i dapp.mmd -o out.svg")
                .start()
        Awaitility.await().atMost(Duration.ofSeconds(30)).until {
            target.resolve("out.svg").exists()
        }
        return sections to documents
    }

    @Test
    fun multiModule() {
        val (sections, documents) = generateSvg("/multi", "a", "f")
        assertThat(sections).hasSize(6)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun multiNestedModule() {
        val (sections, documents) = generateSvg("/multi", "c.nested")
        assertThat(sections).hasSize(5)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun byteArray() {
        val (sections, documents) = generateSvg("/byte_array", "byte_array")
        assertThat(sections).hasSize(1)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun decimal() {
        val (sections, documents) = generateSvg("/decimal", "decimal")
        assertThat(sections).hasSize(1)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun big_integer() {
        val (sections, documents) = generateSvg("/big_integer", "big_integer")
        assertThat(sections).hasSize(1)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun rowId() {
        val (sections, documents) = generateSvg("/rowid", "rowid")
        assertThat(sections).hasSize(1)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun entity() {
        val (sections, documents) = generateSvg("/entity", "entity")
        assertThat(sections).hasSize(1)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun namespace() {
        val (sections, documents) = generateSvg("/namespace", "ns_test")
        assertThat(sections).hasSize(2)
        assertThat(documents).hasSize(1)
    }
}
