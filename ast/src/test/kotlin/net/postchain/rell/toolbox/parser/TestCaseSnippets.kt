package net.postchain.rell.toolbox.parser

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import net.postchain.rell.base.utils.ide.IdeCodeSnippet


object TestCaseSnippets {
    private val testDataPath = "test-cases"

    fun getTestCases(): List<IdeCodeSnippet> {
        val mapper = jacksonObjectMapper()

        val cases = getTestCaseFiles()
        val caseNodes = cases.flatMap { file ->
            mapper.readTree(file)
        }
        return IdeCodeSnippet.deserialize(caseNodes.toString())
    }

    private fun getTestCaseFiles(): List<File> {
        val testCasesFolder = File(RellParserTest::class.java.classLoader.getResource(testDataPath)!!.file)
        return testCasesFolder.walk().filter { it.isFile && it.extension == "json" }.toList()
    }
}