/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.runcfg

import net.postchain.rell.base.utils.Bytes
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.toImmMap
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

sealed interface GeneralDir {
    fun absolutePath(path: String): String
    fun parentPath(path: String): String
    fun subPath(path1: String, path2: String): String
    fun readTextOpt(path: String): String?

    fun readText(path: String): String = requireNotNull(readTextOpt(path)) {
        "File not found: ${absolutePath(path)}"
    }
}


class PathGeneralDir(private val dir: Path): GeneralDir {
    override fun absolutePath(path: String): String = pathToFile(path).absolutePathString()

    override fun parentPath(path: String): String =
        checkNotNull(pathToFile(path).parent.pathString) { "Parent file is null." }

    override fun subPath(path1: String, path2: String): String = Path(path1).resolve(path2).pathString

    override fun readTextOpt(path: String): String? {
        val file = pathToFile(path)
        if (file.notExists()) return null
        return file.readText()
    }

    private fun pathToFile(path: String): Path {
        val file = Path(path)
        return if (file.isAbsolute) file else dir.resolve(path)
    }
}

class MapGeneralDir(private val files: ImmMap<String, String>): GeneralDir {
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

sealed interface DirFile {
    fun previewText(): String
    fun writeTo(filePath: String)
}

class TextDirFile(val text: String): DirFile {
    override fun previewText() = text
    override fun writeTo(filePath: String) = Path(filePath).writeText(text)
}

class BinaryDirFile(val data: Bytes): DirFile {
    override fun previewText() = "<binary file, ${data.size()} bytes>"

    override fun writeTo(filePath: String) = Path(filePath).writeBytes(data.toByteArray())
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
