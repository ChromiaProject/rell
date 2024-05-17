/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_AppContext
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTarget
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTargetBase
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTarget_Regular
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget_ExtendableUserFunction
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.toImmList

class C_ExtendableFunctionDescriptor(
    val uid: R_ExtendableFunctionUid,
    val moduleName: R_ModuleName,
    val fullName: R_FullName?,
    private val headerGetter: C_LateGetter<C_UserFunctionHeader>,
) {
    fun header() = headerGetter.get()
}

class C_FunctionExtensions(
    val uid: R_ExtendableFunctionUid,
    val base: R_FunctionExtension?,
    extensions: List<R_FunctionExtension>,
) {
    val extensions = extensions.toImmList()

    fun toR(): R_FunctionExtensions {
        val allExts = if (base == null) extensions else (extensions + listOf(base)).toImmList()
        return R_FunctionExtensions(uid, allExts)
    }
}

class C_FunctionExtensionsTable(list: List<C_FunctionExtensions>) {
    val list = list.toImmList()

    init {
        for ((i, c) in this.list.withIndex()) {
            checkEquals(c.uid.id, i)
        }
    }

    fun toR(): R_FunctionExtensionsTable {
        val rList = list.map { it.toR() }
        return R_FunctionExtensionsTable(rList)
    }
}

class C_ExtendableUserGlobalFunction(
    appCtx: C_AppContext,
    rFunction: R_FunctionDefinition,
    private val extFnUid: R_ExtendableFunctionUid,
    moduleName: R_ModuleName,
    fullName: R_FullName?,
    private val typePos: S_Pos,
): C_UserGlobalFunction(rFunction) {
    private val msgCtx = appCtx.msgCtx

    private val descriptor = C_ExtendableFunctionDescriptor(extFnUid, moduleName, fullName, headerGetter)

    private val rDescriptorLazy: R_ExtendableFunctionDescriptor by lazy {
        val combiner = compileCombiner()
        R_ExtendableFunctionDescriptor(extFnUid, combiner)
    }

    override fun getExtendableDescriptor() = descriptor

    override fun compileCallTarget(base: C_FunctionCallTargetBase, retType: R_Type?): C_FunctionCallTarget {
        val rDescriptor = rDescriptorLazy
        return C_FunctionCallTarget_ExtendableUserFunction(base, retType, rFunction, rDescriptor)
    }

    fun compileDefinition() {
        rDescriptorLazy // Force descriptor compilation.
    }

    private fun compileCombiner(): R_ExtendableFunctionCombiner {
        C_LateInit.checkPass(C_CompilerPass.EXPRESSIONS)
        val header = headerGetter.get()
        val resType = header.returnType()
        return when (resType) {
            R_UnitType -> R_ExtendableFunctionCombiner_Unit
            R_BooleanType -> R_ExtendableFunctionCombiner_Boolean
            is R_NullableType -> R_ExtendableFunctionCombiner_Nullable
            is R_ListType -> R_ExtendableFunctionCombiner_List(resType)
            is R_MapType -> R_ExtendableFunctionCombiner_Map(resType)
            R_CtErrorType -> R_ExtendableFunctionCombiner_Unit
            else -> {
                msgCtx.error(typePos, "fn:extendable:type:${resType.strCode()}",
                        "Invalid type for extendable function: ${resType.str()}")
                R_ExtendableFunctionCombiner_Unit
            }
        }
    }
}

private class C_FunctionCallTarget_ExtendableUserFunction(
    base: C_FunctionCallTargetBase,
    retType: R_Type?,
    private val rBaseFunction: R_FunctionDefinition,
    private val descriptor: R_ExtendableFunctionDescriptor
): C_FunctionCallTarget_Regular(base, retType) {
    override fun createVTarget(): V_FunctionCallTarget {
        return V_FunctionCallTarget_ExtendableUserFunction(rBaseFunction, descriptor)
    }
}

//TODO OPTIMIZE For REPL this will create a full copy of all extensions per command.
class C_ExtendableFunctionCompiler(oldState: C_FunctionExtensionsTable?) {
    private val fns = mutableListOf<C_ExtFnEntry>()
    private var done = false

    init {
        for (fn in oldState?.list ?: listOf()) {
            fns.add(C_ExtFnEntry(fn.uid, fn.base, fn.extensions))
        }
    }

    fun addExtendableFunction(name: String, base: R_FunctionExtension?): R_ExtendableFunctionUid {
        val id = R_ExtendableFunctionUid(fns.size, name)
        fns.add(C_ExtFnEntry(id, base, listOf()))
        return id
    }

    fun addExtension(extFnUid: R_ExtendableFunctionUid, ext: R_FunctionExtension) {
        val entry = fns[extFnUid.id]
        checkEquals(entry.id, extFnUid)
        entry.exts.add(ext)
    }

    fun compileExtensions(): C_FunctionExtensionsTable {
        check(!done)
        done = true
        val list = fns.map { it.compile() }
        return C_FunctionExtensionsTable(list)
    }

    private class C_ExtFnEntry(
        val id: R_ExtendableFunctionUid,
        val base: R_FunctionExtension?,
        exts: List<R_FunctionExtension>,
    ) {
        val exts = exts.toMutableList()

        fun compile(): C_FunctionExtensions {
            return C_FunctionExtensions(id, base, exts.toImmList())
        }
    }
}
