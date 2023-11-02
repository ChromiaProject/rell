package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath

class CompoundSourceDir(private val dir1: C_SourceDir, private val dir2: C_SourceDir): C_SourceDir() {

    override fun dir(path: C_SourcePath): Boolean {
        var res = dir1.dir(path);
        if (!res) {
            res = dir2.dir(path);
        }
        return res;
    }

    override fun dirs(path: C_SourcePath): List<String> {
        val dirs1 = dir1.dirs(path);
        val dirs2 = dir2.dirs(path);
        return combineMembers(dirs1, dirs2);
    }

    override fun file(path: C_SourcePath): C_SourceFile? {
        var file = dir1.file(path);
        if (file == null) {
            file = dir2.file(path);
        }
        return file;
    }

    override fun files(path: C_SourcePath): List<String> {
        val files1 = dir1.files(path);
        val files2 = dir2.files(path);
        return combineMembers(files1, files2);
    }

    private fun combineMembers(members1: List<String>, members2: List<String> ): List<String>  {
        if (members1.isEmpty()) {
            return members2;
        } else if (members2.isEmpty()) {
            return members1;
        }
        //TODO: WHY LINKED SET?
        val set1 = linkedSetOf(members1)
        set1.addAll(linkedSetOf(members2))
        //FIX LINKED SETS TO LIST
        return listOf("")
    }
}