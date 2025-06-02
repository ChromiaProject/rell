/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import java.io.File

abstract class GeneralDir {
    abstract fun absolutePath(path: String): String
    abstract fun parentPath(path: String): String
    abstract fun subPath(path1: String, path2: String): String

    abstract fun readTextOpt(path: String): String?

    fun readText(path: String): String {
        val res = readTextOpt(path)
        if (res == null) {
            val fullPath = absolutePath(path)
            throw IllegalArgumentException("File not found: $fullPath")
        }
        return res
    }
}

class DiskGeneralDir(private val dir: File): GeneralDir() {
    override fun absolutePath(path: String) = pathToFile(path).absolutePath
    override fun parentPath(path: String) = pathToFile(path).parent
    override fun subPath(path1: String, path2: String) = File(path1, path2).path

    override fun readTextOpt(path: String): String? {
        val file = pathToFile(path)
        if (!file.exists()) return null
        val text = file.readText()
        return text
    }

    private fun pathToFile(path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else File(dir, path)
    }
}

class MapGeneralDir(private val files: ImmMap<String, String>): GeneralDir() {
    override fun absolutePath(path: String) = normalPath(path)

    override fun parentPath(path: String): String {
        val parts = splitPath(path)
        check(!parts.isEmpty())
        val res = joinPath(parts.subList(0, parts.size - 1))
        return res
    }

    override fun subPath(path1: String, path2: String): String {
        val p1 = splitPath(path1)
        val p2 = splitPath(path2)
        val res = joinPath(p1 + p2)
        return res
    }

    override fun readTextOpt(path: String): String? {
        val normPath = normalPath(path)
        val res = files[normPath]
        return res
    }

    private fun normalPath(path: String) = joinPath(splitPath(path))
    private fun splitPath(path: String) = if (path == "") listOf() else path.split("/+").toList()
    private fun joinPath(path: List<String>) = path.joinToString("/")
}

sealed class DirFile {
    abstract fun previewText(): String
    abstract fun write(file: File)
}

class TextDirFile(val text: String): DirFile() {
    override fun previewText() = text
    override fun write(file: File) = file.writeText(text)
}

class BinaryDirFile(val data: Bytes): DirFile() {
    override fun previewText() = "<binary file, ${data.size()} bytes>"
    override fun write(file: File) = file.writeBytes(data.toByteArray())
}

class DirBuilder {
    private val files = mutableMapOf<String, DirFile>()

    fun put(path: String, file: DirFile) {
        check(path.isNotBlank())
        check(path !in files) { "Duplicate file: $path" }
        files[path] = file
    }

    fun put(path: String, text: String) {
        put(path, TextDirFile(text))
    }

    fun put(path: String, data: Bytes) {
        put(path, BinaryDirFile(data))
    }

    fun put(map: Map<String, DirFile>) {
        for ((path, file) in map) {
            put(path, file)
        }
    }

    fun toFileMap() = files.toImmMap()
}
