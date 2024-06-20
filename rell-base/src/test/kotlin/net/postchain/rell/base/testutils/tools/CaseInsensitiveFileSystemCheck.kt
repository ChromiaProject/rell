/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils.tools

import com.google.common.io.Files
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.checkEquals
import java.io.File

/*
Testing C_DiskSourceDir Behavior on Case-Insensitive File Systems

The C_DiskSourceDir class must always work like if the underlying file system is case-sensitive. This is not the case
for Windows and macOS, so additional handling and testing is needed.

To test case-insensitive FS handling under Linux, create and mount a FAT (MS-DOS) partition image.

1. Create a FAT32 partition image file:

mkdir -p .test/fat
dd if=/dev/zero of=.test/fat.img bs=1M count=50
mkfs.vfat -F 32 .test/fat.img

2. Mount the FAT32 partition - needs root (sudo):

sudo mount -o loop,uid=$(id -u $USER),gid=$(id -g $USER) .test/fat.img .test/fat

3. Run this program for testing.
*/

private fun testCaseInsensitiveDir(testDir: File) {
    val testFile1 = File(testDir, "test.txt")
    val testFile2 = File(testDir, "TEST.TXT")
    val testFile3 = File(testDir, "Test.Txt")

    writeFile(testFile1, "Test File")

    checkEquals(testDir.list().orEmpty().toList(), listOf("test.txt"))
    checkEquals(readFile(testFile1), "Test File")
    checkEquals(readFile(testFile2), "Test File")
    checkEquals(readFile(testFile3), "Test File")

    println("Directory case-insensitivity test: PASSED")
}

private fun createSrcDir(testDir: File): File {
    val srcDir = File(testDir, "src")
    val libDir = File(srcDir, "Parent/Child")
    check(libDir.mkdirs())
    writeFile(File(libDir, "Library.rell"), "module; //Library.rell")
    return srcDir
}

private fun testSourceDirDirs(cDir: C_SourceDir) {
    checkEquals(cDir.dirs(C_SourcePath.EMPTY), listOf("Parent"))
    checkEquals(cDir.dirs(C_SourcePath.parse("Parent")), listOf("Child"))
    checkEquals(cDir.dirs(C_SourcePath.parse("parent")), listOf())
    checkEquals(cDir.dirs(C_SourcePath.parse("PARENT")), listOf())
    checkEquals(cDir.dirs(C_SourcePath.parse("PaReNt")), listOf())
    checkEquals(cDir.dirs(C_SourcePath.parse("Parent/Child")), listOf())

    println("C_DiskSourceDir.dirs() test: PASSED")
}

private fun testSourceDirFiles(cDir: C_SourceDir) {
    checkEquals(cDir.files(C_SourcePath.EMPTY), listOf())
    checkEquals(cDir.files(C_SourcePath.parse("Parent")), listOf())
    checkEquals(cDir.files(C_SourcePath.parse("Parent/Child")), listOf("Library.rell"))
    checkEquals(cDir.files(C_SourcePath.parse("parent/child")), listOf())
    checkEquals(cDir.files(C_SourcePath.parse("Parent/child")), listOf())
    checkEquals(cDir.files(C_SourcePath.parse("parent/Child")), listOf())
    checkEquals(cDir.files(C_SourcePath.parse("PARENT/CHILD")), listOf())
    checkEquals(cDir.files(C_SourcePath.parse("pARENT/cHILD")), listOf())

    println("C_DiskSourceDir.files() test: PASSED")
}

private fun testSourceDirDir(cDir: C_SourceDir) {
    checkEquals(cDir.dir(C_SourcePath.EMPTY), true)
    checkEquals(cDir.dir(C_SourcePath.parse("Parent")), true)
    checkEquals(cDir.dir(C_SourcePath.parse("parent")), false)
    checkEquals(cDir.dir(C_SourcePath.parse("PARENT")), false)
    checkEquals(cDir.dir(C_SourcePath.parse("PaReNt")), false)
    checkEquals(cDir.dir(C_SourcePath.parse("Parent/Child")), true)
    checkEquals(cDir.dir(C_SourcePath.parse("parent/child")), false)
    checkEquals(cDir.dir(C_SourcePath.parse("Parent/child")), false)
    checkEquals(cDir.dir(C_SourcePath.parse("parent/Child")), false)
    checkEquals(cDir.dir(C_SourcePath.parse("PARENT/CHILD")), false)
    checkEquals(cDir.dir(C_SourcePath.parse("pARENT/cHILD")), false)

    println("C_DiskSourceDir.dir() test: PASSED")
}

private fun testSourceDirFile(cDir: C_SourceDir) {
    checkEquals(cDir.file(C_SourcePath.EMPTY), null)
    checkEquals(cDir.file(C_SourcePath.parse("Parent")), null)
    checkEquals(cDir.file(C_SourcePath.parse("Parent/Child")), null)
    checkEquals(cDir.file(C_SourcePath.parse("Parent/Child/Library.RELL")), null)
    checkEquals(cDir.file(C_SourcePath.parse("Parent/Child/Library.Rell")), null)
    checkEquals(cDir.file(C_SourcePath.parse("Parent/Child/library.rell")), null)
    checkEquals(cDir.file(C_SourcePath.parse("Parent/Child/LIBRARY.RELL")), null)
    checkEquals(cDir.file(C_SourcePath.parse("Parent/Child/LiBrArY.ReLl")), null)

    val f = checkNotNull(cDir.file(C_SourcePath.parse("Parent/Child/Library.rell")))
    checkEquals(f.readText(), "module; //Library.rell")

    println("C_DiskSourceDir.file() test: PASSED")
}

private fun testRellImports(srcDir: File) {
    chkRellImport(srcDir, "Parent.Child.Library", "OK")
    chkRellImport(srcDir, "parent.child.library", "ct_err:import:not_found:parent.child.library")
    chkRellImport(srcDir, "parent.child.Library", "ct_err:import:not_found:parent.child.Library")
    chkRellImport(srcDir, "PARENT.CHILD.LIBRARY", "ct_err:import:not_found:PARENT.CHILD.LIBRARY")
    chkRellImport(srcDir, "PARENT.CHILD.Library", "ct_err:import:not_found:PARENT.CHILD.Library")

    println("Rell imports test: PASSED")
}

private fun chkRellImport(srcDir: File, module: String, expected: String) {
    val cDir = C_SourceDir.uncachedDiskDir(srcDir)
    writeFile(File(srcDir, "main.rell"), "module; import $module;")

    val modSel = C_CompilerModuleSelection(listOf(R_ModuleName.of("main")))
    val actual = RellTestUtils.processApp(cDir, modSel = modSel) { "OK" }
    checkEquals(actual, expected)
}

fun main() {
    val baseDir = File(".test/fat")
    check(baseDir.isDirectory) { "Directory does not exist: $baseDir" }

    val testDir = File(baseDir, "" + System.currentTimeMillis())
    check(testDir.mkdir()) { "mkdir() failed: $testDir" }

    println("Test directory: $testDir")
    println()

    testCaseInsensitiveDir(testDir)

    val srcDir = createSrcDir(testDir)

    val cDir = C_SourceDir.uncachedDiskDir(srcDir)
    testSourceDirDirs(cDir)
    testSourceDirFiles(cDir)
    testSourceDirDir(cDir)
    testSourceDirFile(cDir)

    testRellImports(srcDir)

    println()
    println("ALL TESTS PASSED")
}

private fun readFile(file: File): String = Files.asCharSource(file, Charsets.UTF_8).read()
private fun writeFile(file: File, text: String) = Files.asCharSink(file, Charsets.UTF_8).write(text)
