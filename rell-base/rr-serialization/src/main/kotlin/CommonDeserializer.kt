/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_FrameBlock
import net.postchain.rell.base.model.rr.RR_FrameDescriptor
import rell.ir.CallFrame
import rell.ir.FrameBlock
import rell.ir.SourcePos
import rell.ir.DefinitionName as FbDefinitionName
import rell.ir.ModuleName as FbModuleName
import rell.ir.MountName as FbMountName

fun deserializeModuleName(fb: FbModuleName?): ModuleName {
    if (fb == null) return ModuleName.EMPTY
    val parts = (0 until fb.partsLength).map { fb.parts(it) }
    return ModuleName.of(parts.joinToString("."))
}

fun deserializeMountName(fb: FbMountName?): MountName {
    if (fb == null) return MountName.EMPTY
    val parts = (0 until fb.partsLength).map { fb.parts(it) }
    return MountName.of(parts.joinToString("."))
}

fun deserializeDefinitionName(fb: FbDefinitionName?): DefinitionName {
    if (fb == null) return DefinitionName("", "", "")
    return DefinitionName(
        module = fb.module,
        qualifiedName = fb.qualifiedName,
        simpleName = fb.simpleName,
    )
}

fun deserializeFilePos(fb: SourcePos?): FilePos {
    if (fb == null) return FilePos("?", 0)
    return FilePos(fb.file ?: "?", fb.line)
}

fun deserializeErrorPos(fb: SourcePos?): ErrorPos {
    if (fb == null) return ErrorPos("?", 0)
    return ErrorPos(fb.file ?: "?", fb.line)
}

fun deserializeFrameBlock(fb: FrameBlock?): RR_FrameBlock {
    if (fb == null) return RR_FrameBlock(parentUid = null, uid = 0, offset = 0, size = 0)
    return RR_FrameBlock(
        parentUid = fb.parentUid?.toLong(),
        uid = fb.uid.toLong(),
        offset = fb.offset,
        size = fb.size,
    )
}

fun deserializeFrameDescriptor(fb: CallFrame?): RR_FrameDescriptor {
    if (fb == null) return RR_FrameDescriptor(
        size = 0,
        rootBlock = RR_FrameBlock(parentUid = null, uid = 0, offset = 0, size = 0),
        hasGuardBlock = false,
    )
    return RR_FrameDescriptor(
        size = fb.size,
        rootBlock = deserializeFrameBlock(fb.rootBlock),
        hasGuardBlock = fb.hasGuardBlock,
    )
}
