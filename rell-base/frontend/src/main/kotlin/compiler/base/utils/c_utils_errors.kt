/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.compiler.ast.S_Name
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.def.C_MntEntry
import net.postchain.rell.base.compiler.base.expr.C_AtFromImplicitAttr
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.model.*

object C_Errors {
    fun errTypeMismatch(srcType: R_Type, dstType: R_Type, errCode: String, errMsg: String): C_CodeMsg {
        val code = "$errCode:[${dstType.strCode()}]:[${srcType.strCode()}]"
        val msg = "$errMsg: ${srcType.str()} instead of ${dstType.str()}"
        return code toCodeMsg msg
    }

    fun errTypeMismatch(
        msgCtx: C_MessageContext,
        pos: S_Pos,
        srcType: R_Type,
        dstType: R_Type,
        errCode: String,
        errMsg: String,
    ) {
        val codeMsg = errTypeMismatch(srcType, dstType, errCode, errMsg)
        msgCtx.error(pos, codeMsg)
    }

    fun errTypeMismatch(
        msgCtx: C_MessageContext,
        pos: S_Pos,
        srcType: R_Type,
        dstType: R_Type,
        errSupplier: C_CodeMsgSupplier,
    ) {
        if (srcType.isNotError() && dstType.isNotError()) {
            msgCtx.error(errTypeMismatch(pos, srcType, dstType, errSupplier))
        }
    }

    fun errTypeMismatch(pos: S_Pos, srcType: R_Type, dstType: R_Type, errSupplier: C_CodeMsgSupplier): C_Error {
        val errCodeMsg = errSupplier()
        val codeMsg = errTypeMismatch(srcType, dstType, errCodeMsg.code, errCodeMsg.msg)
        return C_Error.stop(pos, codeMsg)
    }

    fun errMultipleAttrs(pos: S_Pos, attrs: List<C_AtFromImplicitAttr>, errCode: String, errMsg: String): C_Error {
        val attrNames = attrs.map { it.attrNameMsg() }
        val attrsCode = attrNames.joinToString(",") { it.code }
        val attrsText = attrNames.joinToString(", ") { it.msg }
        return C_Error.stop(pos, "$errCode:[$attrsCode]", "$errMsg: $attrsText")
    }

    fun errUnknownName(name: C_Name): C_PosCodeMsg {
        val nameStr = name.str
        return C_PosCodeMsg(name.pos, "unknown_name:$nameStr", "Unknown name: '$nameStr'")
    }

    fun errUnknownAttr(name: C_Name): C_Error {
        return C_Error.stop(name.pos, "expr_attr_unknown:$name", "Unknown attribute: '$name'")
    }

    fun errUnknownName(msgCtx: C_MessageContext, pos: S_Pos, nameCode: String, nameMsg: String) {
        msgCtx.error(pos, "unknown_name:$nameCode", "Unknown name: '$nameMsg'")
    }

    fun errUnknownMember(msgCtx: C_MessageContext, type: R_Type, name: C_Name) {
        if (type.isNotError()) {
            msgCtx.error(name.pos, "unknown_member:[${type.strCode()}]:$name",
                    "Type ${type.strCode()} has no member '$name'")
        }
    }

    fun errFunctionNoSql(pos: S_Pos, name: String): C_Error {
        return C_Error.stop(pos, "expr_call_nosql:$name", "Function '$name' cannot be converted to SQL")
    }

    fun errLibFunctionNamedArg(msgCtx: C_MessageContext, fnName: String, arg: S_Name) {
        val msg = msgSysFunctionNamedArg(fnName, arg)
        msgCtx.error(arg.pos, msg)
    }

    private fun msgSysFunctionNamedArg(fnName: String, arg: S_Name): C_CodeMsg {
        return C_CodeMsg("expr:call:sys_global_named_arg:$arg", "Named arguments not supported for function '$fnName'")
    }

    fun errNamedArgsNotSupported(msgCtx: C_MessageContext, fn: Lazy<String>?, arg: C_Name) {
        val fnCode = fn?.value ?: ""
        val fnMsg = if (fn == null) "this function" else "function '${fn.value}'"
        msgCtx.error(arg.pos, "expr:call:named_args_not_allowed:[$fnCode]:$arg", "Named arguments not supported for $fnMsg")
    }

    fun errBadDestination(pos: S_Pos): C_Error {
        return C_Error.stop(pos, "expr_bad_dst", "Invalid assignment destination")
    }

    fun errAttrNotMutable(pos: S_Pos, name: String, fullName: String): C_Error {
        return C_Error.stop(pos, msgAttrNotMutable(name, fullName))
    }

    fun msgAttrNotMutable(name: String, fullName: String): C_CodeMsg {
        return C_CodeMsg("attr_not_mutable:$fullName", "Attribute '$name' is not mutable")
    }

    fun errExprNoDb(msgCtx: C_MessageContext, pos: S_Pos, type: R_Type) {
        if (type.isNotError()) {
            val typeStr = type.strCode()
            msgCtx.error(pos, "expr_nosql:$typeStr", "Value of type $typeStr cannot be converted to SQL")
        }
    }

    fun errExprDbNotAllowed(pos: S_Pos): C_Error {
        return C_Error.stop(pos, "expr_sqlnotallowed", "Expression cannot be converted to SQL")
    }

    fun errCannotUpdate(msgCtx: C_MessageContext, pos: S_Pos, name: String) {
        msgCtx.error(pos, "stmt_update_cant:$name", "Not allowed to update objects of entity '$name'")
    }

    fun errCannotDelete(pos: S_Pos, name: String): C_Error {
        return C_Error.stop(pos, "stmt_delete_cant:$name", "Not allowed to delete objects of entity '$name'")
    }

    fun errNameConflict(name: C_Name, otherType: C_DeclarationType, otherPos: S_Pos?): C_PosCodeMsg {
        val baseCode = "name_conflict"
        val baseMsg = "Name conflict"
        val codeMsg = if (otherPos != null) {
            val code = "$baseCode:user:$name:$otherType:$otherPos"
            val msg = "$baseMsg: ${otherType.msg} '$name' defined at ${otherPos.strLine()}"
            C_CodeMsg(code, msg)
        } else {
            C_CodeMsg("$baseCode:sys:$name:$otherType", "$baseMsg: system ${otherType.msg} '$name'")
        }
        return C_PosCodeMsg(name.pos, codeMsg)
    }

    fun errMountConflict(
            chain: String?,
            mountName: MountName,
            def: R_Definition,
            pos: S_Pos,
            otherEntry: C_MntEntry,
    ): C_Error {
        val baseCode = "mnt_conflict"
        val commonCode = "[${def.appLevelName}]:$mountName:${otherEntry.type}:[${otherEntry.def.appLevelName}]"
        val baseMsg = "Mount name conflict" + if (chain == null) "" else "(external chain '$chain')"
        val otherNameMsg = otherEntry.def.simpleName

        val code: String
        val msg: String

        if (otherEntry.pos != null) {
            code = "$baseCode:user:$commonCode:${otherEntry.pos}"
            msg = "$baseMsg: ${otherEntry.type.msg} '$otherNameMsg' has mount name '$mountName' " +
                    "(defined at ${otherEntry.pos.strLine()})"
        } else {
            code = "$baseCode:sys:$commonCode"
            msg = "$baseMsg: system ${otherEntry.type.msg} '$otherNameMsg' has mount name '$mountName'"
        }

        return C_Error.stop(pos, code, msg)
    }

    fun errMountConflictSystem(mountName: MountName, def: R_Definition, pos: S_Pos): C_Error {
        val code = "mnt_conflict:sys:[${def.appLevelName}]:$mountName"
        val msg = "Mount name conflict: '$mountName' is a system mount name"
        return C_Error.stop(pos, code, msg)
    }

    fun errDuplicateAttribute(msgCtx: C_MessageContext, name: C_Name) {
        msgCtx.error(name.pos, "dup_attr:$name", "Duplicate attribute: '$name'")
    }

    fun errAttributeTypeUnknown(msgCtx: C_MessageContext, name: C_Name) {
        msgCtx.error(name.pos, "unknown_name_type:$name",
                "Cannot infer type for '$name'; specify type explicitly")
    }

    fun errAtPlaceholderNotDefined(pos: S_Pos): C_Error {
        return C_Error.stop(pos, "expr:placeholder:none", "Placeholder not defined")
    }

    fun errOverrideMissing(msgCtx: C_MessageContext, pos: S_Pos, name: String, defPos: S_Pos) {
        var code = "override:missing:[$name]"
        var msg = "No override for abstract function '$name'"
        if (defPos != pos) {
            code += ":[${defPos.strLine()}]"
            msg += " (defined at ${defPos.strLine()})"
        }
        msgCtx.error(pos, code, msg)
    }

    fun msgPartialCallNotAllowed(fnName: String?): C_CodeMsg {
        val fnCode = fnName ?: "?"
        val fnMsg = if (fnName == null) "this function" else "function '$fnName'"
        return C_CodeMsg("expr:call:partial_not_supported:[$fnCode]", "Partial application not supported for $fnMsg")
    }

    fun msgPartialCallAmbiguous(fnName: String?): C_CodeMsg {
        val fnCode = fnName ?: "?"
        val fnMsg = if (fnName == null) "the function" else "function '$fnName'"
        return C_CodeMsg("expr:call:partial_ambiguous:[$fnCode]", "Cannot determine which variant of $fnMsg to use")
    }

    fun msgModuleNotFound(name: ModuleName): C_CodeMsg {
        return C_CodeMsg("import:not_found:$name", "Module '$name' not found")
    }

    fun check(b: Boolean, pos: S_Pos, errSupplier: C_CodeMsgSupplier) {
        if (!b) {
            val codeMsg = errSupplier()
            throw C_Error.stop(pos, codeMsg)
        }
    }

    fun check(ctx: C_MessageContext, b: Boolean, pos: S_Pos, errSupplier: C_CodeMsgSupplier): Boolean {
        if (!b) {
            val codeMsg = errSupplier()
            ctx.error(pos, codeMsg)
        }
        return b
    }

    fun <T> checkNotNull(value: T?, pos: S_Pos, errSupplier: C_CodeMsgSupplier): T {
        if (value == null) {
            val codeMsg = errSupplier()
            throw C_Error.stop(pos, codeMsg)
        }
        return value
    }

    fun R_AttrValidator.Error.report(ctx: C_MessageContext, pos: S_Pos) {
        ctx.error(pos, code, msg)
    }
}
