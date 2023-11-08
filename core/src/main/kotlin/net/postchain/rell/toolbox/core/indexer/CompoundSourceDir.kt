package net.postchain.rell.toolbox.core.indexer

import com.google.common.collect.ImmutableList
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath


class CompoundSourceDir(dir1: C_SourceDir, dir2: C_SourceDir) : C_SourceDir() {
    private val dir1: C_SourceDir
    private val dir2: C_SourceDir

    init {
        this.dir1 = dir1
        this.dir2 = dir2
    }

    override fun dir(path: C_SourcePath): Boolean {
        var res: Boolean = dir1.dir(path)
        if (!res) {
            res = dir2.dir(path)
        }
        return res
    }

    override fun dirs(path: C_SourcePath): List<String> {
        val dirs1: List<String> = dir1.dirs(path)
        val dirs2: List<String> = dir2.dirs(path)
        return combineMembers(dirs1, dirs2)
    }

    override fun files(path: C_SourcePath): List<String> {
        val files1: List<String> = dir1.files(path)
        val files2: List<String> = dir2.files(path)
        return combineMembers(files1, files2)
    }

    private fun combineMembers(members1: List<String>, members2: List<String>): List<String> {
        if (members1 == null || members1.isEmpty()) {
            return members2
        } else if (members2 == null || members2.isEmpty()) {
            return members1
        }
        val set: MutableSet<String> = LinkedHashSet(members1)
        set.addAll(members2)
        return ImmutableList.copyOf(set)
    }

    override fun file(path: C_SourcePath): C_SourceFile? {
        var file: C_SourceFile? = dir1.file(path)
        if (file == null) {
            file = dir2.file(path)
        }
        return file
    }
}
