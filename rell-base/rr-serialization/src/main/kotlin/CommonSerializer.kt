/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_FrameBlock
import net.postchain.rell.base.model.rr.RR_FrameDescriptor
import rell.ir.CallFrame as FbCallFrame
import rell.ir.DefinitionName as FbDefinitionName
import rell.ir.FrameBlock as FbFrameBlock
import rell.ir.ModuleName as FbModuleName
import rell.ir.MountName as FbMountName
import rell.ir.SourcePos as FbSourcePos

fun SerializerContext.serializeModuleName(name: ModuleName): Int {
    val parts = createStringVector(name.parts.map { it.str })
    return FbModuleName.createModuleName(builder, parts)
}

fun SerializerContext.serializeMountName(name: MountName): Int {
    val parts = createStringVector(name.parts.map { it.str })
    return FbMountName.createMountName(builder, parts)
}

fun SerializerContext.serializeDefinitionName(defName: DefinitionName): Int {
    val module = createString(defName.module)
    val qualified = createString(defName.qualifiedName)
    val simple = createString(defName.simpleName)
    return FbDefinitionName.createDefinitionName(builder, module, qualified, simple)
}

fun SerializerContext.serializeSourcePos(file: String, line: Int): Int {
    val fileOff = createString(file)
    FbSourcePos.startSourcePos(builder)
    FbSourcePos.addFile(builder, fileOff)
    FbSourcePos.addLine(builder, line)
    FbSourcePos.addColumn(builder, 0)
    return FbSourcePos.endSourcePos(builder)
}

fun SerializerContext.serializeFilePos(pos: FilePos): Int = serializeSourcePos(pos.file, pos.line)

internal fun SerializerContext.serializeErrorPos(pos: ErrorPos): Int = serializeSourcePos(pos.file, pos.line)

internal fun SerializerContext.serializeFrameBlock(block: RR_FrameBlock): Int {
    FbFrameBlock.startFrameBlock(builder)
    if (block.parentUid != null) {
        FbFrameBlock.addHasParent(builder, true)
        FbFrameBlock.addParentUid(builder, block.parentUid!!.toUInt())
    }
    FbFrameBlock.addUid(builder, block.uid.toUInt())
    FbFrameBlock.addOffset(builder, block.offset)
    FbFrameBlock.addSize(builder, block.size)
    return FbFrameBlock.endFrameBlock(builder)
}

internal fun SerializerContext.serializeFrameDescriptor(frame: RR_FrameDescriptor): Int {
    val rootBlock = serializeFrameBlock(frame.rootBlock)
    FbCallFrame.startCallFrame(builder)
    FbCallFrame.addSize(builder, frame.size)
    FbCallFrame.addRootBlock(builder, rootBlock)
    FbCallFrame.addHasGuardBlock(builder, frame.hasGuardBlock)
    return FbCallFrame.endCallFrame(builder)
}
