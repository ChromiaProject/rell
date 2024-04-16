package net.postchain.rell.toolbox.core.parser

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.postchain.rell.base.utils.ide.IdeCodeSnippet
import java.io.File

class RellTestCaseSnippet(val file: File, val snippet: IdeCodeSnippet)

object TestCaseSnippets {
    private val testDataPath = "test-cases"

    fun getTestCases(): List<RellTestCaseSnippet> {
        val mapper = jacksonObjectMapper()
        return getTestCaseFiles().flatMap { file ->
            mapper.readTree(file).map { jsonNode ->
                RellTestCaseSnippet(file, IdeCodeSnippet.deserialize(jsonNode.toString()))
            }
        }
    }

    private fun getTestCaseFiles(): List<File> {
        val testCasesFolder = File(RellParserTest::class.java.classLoader.getResource(testDataPath)!!.file)
        return testCasesFolder.walk().filter { it.isFile && it.extension == "json" }.toList()
    }
}