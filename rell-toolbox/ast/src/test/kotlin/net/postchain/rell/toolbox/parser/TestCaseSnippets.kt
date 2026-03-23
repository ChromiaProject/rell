/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.postchain.rell.base.utils.ide.IdeCodeSnippet
import java.io.File

object TestCaseSnippets {
    private const val TEST_DATA_PATH = "test-cases"

    fun getTestCases(): List<IdeCodeSnippet> {
        val mapper = jacksonObjectMapper()

        val cases = getTestCaseFiles()
        val caseNodes = cases.flatMap { file ->
            mapper.readTree(file)
        }
        return IdeCodeSnippet.deserialize(caseNodes.toString())
    }

    private fun getTestCaseFiles(): List<File> {
        val testCasesFolder = File(RellParserTest::class.java.classLoader.getResource(TEST_DATA_PATH)!!.file)
        return testCasesFolder.walk().filter { it.isFile && it.extension == "json" }.toList()
    }
}
