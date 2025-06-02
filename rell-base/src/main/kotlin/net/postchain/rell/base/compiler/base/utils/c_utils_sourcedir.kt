/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeFilePath
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.util.*

class IdeSourcePathFilePath(val path: C_SourcePath): IdeFilePath() {
    override fun equals(other: Any?) = other is IdeSourcePathFilePath && path == other.path
    override fun hashCode() = Objects.hash(javaClass, path)
    override fun toString() = path.str()
}

class C_SourcePath private constructor(val parts: ImmList<String>): Comparable<C_SourcePath> {
    private val str = parts.joinToString("/")

    fun add(path: C_SourcePath) = C_SourcePath(parts + path.parts)

    fun add(part: String): C_SourcePath {
        if (!validate(part)) {
            throw errBadPath(part)
        }
        return C_SourcePath(parts + part)
    }

    fun parent() = C_SourcePath(parts.subList(0, parts.size - 1))
    fun str() = str

    override fun compareTo(other: C_SourcePath) = CommonUtils.compareLists(parts, other.parts)
    override fun equals(other: Any?) = other === this || (other is C_SourcePath && parts == other.parts)
    override fun hashCode() = parts.hashCode()
    override fun toString() = str()

    companion object {
        val EMPTY = C_SourcePath(immListOf())

        fun of(): C_SourcePath = EMPTY

        fun of(parts: List<String>): C_SourcePath {
            if (!validate(parts)) {
                val str = parts.joinToString("/")
                throw errBadPath(str)
            }
            return C_SourcePath(parts.toImmList())
        }

        fun ofOpt(parts: List<String>): C_SourcePath? {
            if (!validate(parts)) {
                return null
            }
            return C_SourcePath(parts.toImmList())
        }

        fun parse(path: String): C_SourcePath {
            val res = parseOpt(path)
            return res ?: throw errBadPath(path)
        }

        fun parseOpt(path: String): C_SourcePath? {
            val parts = StringUtils.splitPreserveAllTokens(path, "/\\").toImmList()
            if (parts.isEmpty()) {
                return null
            }

            if (!validate(parts)) {
                return null
            }

            return C_SourcePath(parts)
        }

        private fun validate(parts: List<String>): Boolean {
            return parts.all { validate(it) }
        }

        private fun validate(part: String): Boolean {
            return part != "" && part != "." && part != ".."
        }

        private fun errBadPath(str: String): C_CommonError {
            return C_CommonError("invalid_path:$str", "Invalid path: '$str'")
        }
    }
}

abstract class C_SourceFile {
    abstract fun idePath(): IdeFilePath
    abstract fun readText(): String

    /**
     * @param version Compatibility version: needed mostly for the compiler to support 0.10.9, which is the oldest
     * supported version with grammar changes (uses the obsolete `sort` keyword). Non-compiler implementations
     * (like IDE) may ignore this parameter, if supporting old syntax versions is not required.
     */
    abstract fun readAst(version: R_LangVersion = RellVersions.VERSION): S_RellFile
}

internal fun C_SourceFile.readAstEx(version: R_LangVersion? = null): S_RellFile {
    return readAst(version ?: RellVersions.VERSION)
}

abstract class C_SourceDir {
    abstract fun dir(path: C_SourcePath): Boolean
    abstract fun file(path: C_SourcePath): C_SourceFile?
    abstract fun dirs(path: C_SourcePath): List<String>
    abstract fun files(path: C_SourcePath): List<String>

    companion object {
        @JvmField
        val EMPTY: C_SourceDir = C_MapSourceDir(mapOf())

        fun mapDir(files: Map<C_SourcePath, C_SourceFile>): C_SourceDir {
            return C_CachedSourceDir(C_MapSourceDir(files))
        }

        @JvmStatic
        fun mapDirOf(files: Map<String, String>): C_SourceDir {
            val files2 = files
                    .mapKeys { (k, _) -> C_SourcePath.parse(k) }
                    .mapValues { (k, v) -> C_TextSourceFile(k, v) }
            return mapDir(files2)
        }

        @JvmStatic
        fun mapDirOf(vararg files: Pair<String, String>): C_SourceDir {
            val files2 = mapOf(*files)
            return mapDirOf(files2)
        }

        @JvmStatic
        fun diskDir(dir: File): C_SourceDir {
            return C_CachedSourceDir(C_DiskSourceDir(dir))
        }

        @JvmStatic
        fun uncachedDiskDir(dir: File): C_SourceDir {
            return C_DiskSourceDir(dir)
        }
    }
}

class C_TextSourceFile(
    private val path: C_SourcePath,
    private val text: String,
): C_SourceFile() {
    private val idePath: IdeFilePath = IdeSourcePathFilePath(path)

    override fun idePath(): IdeFilePath = idePath

    override fun readAst(version: R_LangVersion): S_RellFile {
        return C_Parser.parse(path, idePath, text, version)
    }

    override fun readText() = text
}

private class C_MapSourceDir(files: Map<C_SourcePath, C_SourceFile>): C_SourceDir() {
    private val root = buildTree(files)

    override fun dir(path: C_SourcePath): Boolean {
        val dir = findDir(path.parts)
        return dir != null
    }

    override fun file(path: C_SourcePath): C_SourceFile? {
        val n = path.parts.size
        if (n == 0) return null

        val dir = findDir(path.parts.subList(0, n - 1))
        dir ?: return null

        val file = dir.files[path.parts[n - 1]]
        return file
    }

    override fun dirs(path: C_SourcePath): List<String> {
        val dir = findDir(path.parts)
        dir ?: return listOf()
        return dir.dirs.keys.sorted().toImmList()
    }

    override fun files(path: C_SourcePath): List<String> {
        val dir = findDir(path.parts)
        dir ?: return listOf()
        return dir.files.keys.sorted().toImmList()
    }

    private fun findDir(path: List<String>): DirNode? {
        var dir = root
        for (part in path) {
            val next = dir.dirs[part]
            next ?: return null
            dir = next
        }
        return dir
    }

    private class DirNode(val dirs: ImmMap<String, DirNode>, val files: ImmMap<String, C_SourceFile>)

    companion object {
        private fun buildTree(map: Map<C_SourcePath, C_SourceFile>): DirNode {
            for (path in map.keys) {
                check(path.parts.isNotEmpty())
            }
            return buildTree(map, 0)
        }

        private fun buildTree(map: Map<C_SourcePath, C_SourceFile>, pos: Int): DirNode {
            val files = mutableMapOf<String, C_SourceFile>()
            val subs = mutableMapOf<String, MutableMap<C_SourcePath, C_SourceFile>>()

            for ((path, file) in map) {
                val len = path.parts.size
                val name = path.parts[pos]
                if (len == pos + 1) {
                    files[name] = file
                } else {
                    val sub = subs.computeIfAbsent(name) { mutableMapOf() }
                    sub[path] = file
                }
            }

            val dirs = subs.mapValues { buildTree(it.value, pos + 1) }
            return DirNode(dirs.toImmMap(), files.toImmMap())
        }
    }
}

private class C_DiskSourceDir(private val dir: File): C_SourceDir() {
    override fun dir(path: C_SourcePath): Boolean {
        val file = toFile(path)
        return file != null && file.isDirectory
    }

    override fun file(path: C_SourcePath): C_SourceFile? {
        val file = toFile(path)
        return if (file != null && file.isFile) C_DiskSourceFile(file, path) else null
    }

    override fun dirs(path: C_SourcePath): List<String> {
        return members(path) { it.isDirectory }
    }

    override fun files(path: C_SourcePath): List<String> {
        return members(path) { it.isFile }
    }

    private fun members(path: C_SourcePath, filter: (File) -> Boolean): List<String> {
        val file = toFile(path)
        val files = file?.listFiles() ?: arrayOf<File>()
        return files.filter(filter).map { it.name }.sorted()
    }

    private fun toFile(path: C_SourcePath): File? {
        var curFile = dir
        for (part in path.parts) {
            val files = curFile.list()
            if (files == null || part !in files) return null
            curFile = File(curFile, part)
        }
        return curFile
    }

    private class C_DiskSourceFile(private val file: File, private val sourcePath: C_SourcePath): C_SourceFile() {
        private val idePath: IdeFilePath = IdeSourcePathFilePath(sourcePath)

        override fun idePath() = idePath

        override fun readAst(version: R_LangVersion): S_RellFile {
            val text = readText()
            return C_Parser.parse(sourcePath, idePath, text, version)
        }

        override fun readText() = file.readText()
    }
}

private class C_CachedSourceDir(private val sourceDir: C_SourceDir): C_SourceDir() {
    private val cache = mutableMapOf<C_SourcePath, CacheEntry>()

    override fun dir(path: C_SourcePath): Boolean {
        val e = lookup(path)
        var dir = e.dir
        if (dir == null) {
            dir = sourceDir.dir(path)
            e.dir = dir
        }
        return dir
    }

    override fun file(path: C_SourcePath): C_SourceFile? {
        val e = lookup(path)
        var file = e.file
        if (!e.fileKnown) {
            file = sourceDir.file(path)
            if (file != null) {
                file = C_CachedSourceFile(file)
            }
            e.file = file
            e.fileKnown = true
        }
        return file
    }

    override fun dirs(path: C_SourcePath): List<String> {
        val e = lookup(path)
        var dirs = e.dirs
        if (dirs == null) {
            dirs = sourceDir.dirs(path)
            if (dirs.isNotEmpty()) e.dir = true
            e.dirs = dirs
        }
        return dirs
    }

    override fun files(path: C_SourcePath): List<String> {
        val e = lookup(path)
        var files = e.files
        if (files == null) {
            files = sourceDir.files(path)
            if (files.isNotEmpty()) e.dir = true
            e.files = files
        }
        return files
    }

    private fun lookup(path: C_SourcePath): CacheEntry = cache.computeIfAbsent(path) { CacheEntry() }

    private class C_CachedSourceFile(private val file: C_SourceFile): C_SourceFile() {
        private val idePath = CachedField { file.idePath() }
        private val text = CachedField { file.readText() }
        private val astMap = mutableMapOf<R_LangVersion, CachedField<S_RellFile>>()

        override fun idePath(): IdeFilePath {
            return idePath.get()
        }

        override fun readAst(version: R_LangVersion): S_RellFile {
            val field = astMap.computeIfAbsent(version) {
                CachedField { file.readAst(it) }
            }
            return field.get()
        }

        override fun readText(): String {
            return text.get()
        }

        private class CachedField<T>(private val f: () -> T) {
            private var value: T? = null
            private var error: Exception? = null

            fun get(): T {
                var res = value
                if (res != null) {
                    return res
                }

                val err = error
                if (err != null) {
                    throw err
                }

                try {
                    res = f()
                    value = res
                    return res
                } catch (e: Exception) {
                    error = e
                    throw e
                }
            }
        }
    }

    private class CacheEntry {
        var dir: Boolean? = null
        var fileKnown = false
        var file: C_SourceFile? = null
        var dirs: List<String>? = null
        var files: List<String>? = null
    }
}
