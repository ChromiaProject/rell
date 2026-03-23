/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class CodeGeneratorTest {

    @Test
    fun `Multiple sections are added only once`() {
        val generator = CodeGenerator(TestDocumentFactory(), TestCodegeneratorConfig())
        val docs = generator.constructDocuments(listOf(
                TestEntity("foo"),
                TestEntity("foo")
        ))
        assertThat(docs["test/test.tst"]!!.format().split("\n").filter { it == "foo" }.size).isEqualTo(1)
    }
}
