/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.cli

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiGenCliTest {
    private val bridPattern = Regex("[0-9A-F]{64}")

    private fun runMultigen(
        command: String,
        code: Int = 0,
        stderr: String = "",
    ): Path {
        val dir = createTempDirectory("multigen-test-")
        assertTrue(dir.listDirectoryEntries().isEmpty())
        CliTestUtils.chkCommand(command.replace("%s", dir.toAbsolutePath().toString()), code = code, stderr = stderr)
        return dir
    }

    @Test
    fun testRunSimpleGenerate() {
        val dir = runMultigen("multigen.sh -d work/testproj/src -o %s work/testproj/config/run-simple.xml")

        val files = dir.useDirectoryEntries { s -> s.map(Path::name).toSet() }
        assertTrue("node-config.properties" in files)
        assertTrue("private.properties" in files)
        assertTrue("blockchains" in files)

        assertEquals(listOf("1"), (dir / "blockchains").listDirectoryEntries().map { it.name })

        val chainFiles = (dir / "blockchains/1").listDirectoryEntries().map { it.name }.sorted()
        assertEquals(listOf("0.gtv", "0.xml", "brid.txt"), chainFiles)

        val brid = (dir / "blockchains/1/brid.txt").readText()
        assertTrue(bridPattern.matches(brid), "BRID format mismatch: $brid")
    }

    @Test
    fun testRunxmlDocsSample() {
        val dir = runMultigen("multigen.sh -d work/runxml-docs-sample/src -o %s work/runxml-docs-sample/run.xml")

        val files = dir.useDirectoryEntries { it.map(Path::name).toSet() }
        assertTrue("node-config.properties" in files)
        assertTrue("private.properties" in files)
        assertTrue("blockchains" in files)

        assertEquals(listOf("1", "2"), (dir / "blockchains").listDirectoryEntries().map { it.name }.sorted())
        assertEquals(
            listOf("0.gtv", "0.xml", "1000.gtv", "1000.xml", "brid.txt"),
            (dir / "blockchains/1").listDirectoryEntries().map { it.name }.sorted(),
        )
        assertEquals(
            listOf("0.gtv", "0.xml", "1000.gtv", "1000.xml", "2000.gtv", "2000.xml", "3000.gtv", "3000.xml", "brid.txt"),
            (dir / "blockchains/2").listDirectoryEntries().map { it.name }.sorted(),
        )

        val brid1 = (dir / "blockchains/1/brid.txt").readText()
        assertTrue(bridPattern.matches(brid1), "BRID1 format mismatch: $brid1")

        val brid2 = (dir / "blockchains/2/brid.txt").readText()
        assertTrue(bridPattern.matches(brid2), "BRID2 format mismatch: $brid2")
    }

    @Test
    fun testModargsOk() {
        runMultigen("multigen.sh -d work/testproj/src -o %s work/testproj/config/run-modargs-ok.xml")
    }

    @Test
    fun testModargsMissing() {
        runMultigen(
            "multigen.sh -d work/testproj/src -o %s work/testproj/config/run-modargs-missing.xml",
            code = 2,
            stderr = "ERROR: Missing module_args for module(s): modargs.bar\n",
        )
    }

    @Test
    fun testModargsWrong() {
        runMultigen(
            "multigen.sh -d work/testproj/src -o %s work/testproj/config/run-modargs-wrong.xml",
            code = 2,
            stderr = "ERROR: Bad module_args for module 'modargs.bar': Decoding type 'text': expected STRING, actual INTEGER (attribute: modargs.bar:module_args.a)\n",
        )
    }

    @Test
    fun testModargsExtra() {
        runMultigen("multigen.sh -d work/testproj/src -o %s work/testproj/config/run-modargs-extra.xml")
    }
}
