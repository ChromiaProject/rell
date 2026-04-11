/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.postchain.rell.base.utils.ide.IdeCodeSnippet
import java.nio.file.Files
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.toPath

internal object TestCaseSnippets {
    private const val TEST_DATA_PATH = "test-cases"
    private val mapper = jacksonObjectMapper()

    /**
     * Returns a sequential stream of test cases deserialized from JSON files under the `test-cases` classpath resource.
     *
     * The stream holds a directory walker and MUST be closed after use.
     * JUnit 5's `@MethodSource` closes it automatically.
     *
     * Callers that want parallel deserialization can call `.parallel()` before their terminal operation, but note
     * that fine-grained per-element work tends to perform worse under parallel execution due to ForkJoinPool
     * dispatch overhead and JaCoCo instrumentation contention on shared atomics.
     */
    fun getTestCases(): Stream<IdeCodeSnippet> {
        val testDataFolder = RellParserTest::class.java.classLoader.getResource(TEST_DATA_PATH)!!.toURI().toPath()

        return Files.walk(testDataFolder)
            .filter { it.isRegularFile() && it.extension == "json" }
            .flatMap { mapper.readValue<List<Map<String, Any>>>(it.toFile()).stream() }
            .map(IdeCodeSnippet::deserializeFromRaw)
    }
}
