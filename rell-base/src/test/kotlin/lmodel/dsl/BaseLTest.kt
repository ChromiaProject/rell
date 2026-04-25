/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.*
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty_RtValue
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_Module
import net.postchain.rell.base.lmodel.L_TypeDefMembers
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_MemberCalculator
import net.postchain.rell.base.model.expr.R_MemberCalculator_Error
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.rtValueToRRConstant
import net.postchain.rell.base.runtime.simple
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.doc.DocException
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocUtils
import net.postchain.rell.base.utils.toImmList
import kotlin.test.assertEquals

abstract class BaseLTest {
    init {
        // Touch Lib_Rell to trigger its init block which registers builtin R_LibUniqueType → C_LibTypeDef bindings.
        // Without this, tests that use stdlib types (e.g. "integer") via L_FunctionHeader.strCode hit
        // R_LibUniqueType.getLibType0 with an empty registry.
        Lib_Rell.MODULE
    }

    protected fun makeModule(name: String, block: Ld_ModuleDsl.() -> Unit): L_Module {
        val modCfg = Ld_ModuleConfig(requireSince = false)
        return Ld_ModuleDsl.make(name, modCfg, block)
    }

    protected fun chkDefs(mod: L_Module, vararg expected: String) {
        val defs = mod.namespace.getAllDefs()
        val exp = expected.toList()
        val act = defs.map { it.strCode() }
        assertEquals(exp, act)
    }

    protected fun chkTypeMems(mod: L_Module, typeName: String, vararg expected: String) {
        chkTypeMems0(mod, typeName, false, expected.toImmList())
    }

    protected fun chkTypeAllMems(mod: L_Module, typeName: String, vararg expected: String) {
        chkTypeMems0(mod, typeName, true, expected.toImmList())
    }

    private fun chkTypeMems0(mod: L_Module, typeName: String, all: Boolean, expected: List<String>) {
        val rTypeName = QualifiedName.of(typeName)
        val typeDef = mod.getTypeDefOrNull(rTypeName)
        val typeExt = mod.getTypeExtensionOrNull(rTypeName)
        val members: L_TypeDefMembers = when {
            typeDef != null -> if (all) typeDef.allMembers else typeDef.members
            typeExt != null -> typeExt.members
            else -> throw IllegalArgumentException(typeName)
        }

        val exp = expected.toImmList()
        val act = members.all.map { it.strCode() }
        assertEquals(exp, act)
    }

    protected fun chkModuleErr(exp: String, block: Ld_ModuleDsl.() -> Unit) {
        val act = try {
            makeModule("test", block)
            "OK"
        } catch (e: Ld_Exception) {
            "LDE:${e.code}"
        }
        assertEquals(exp, act)
    }

    protected fun chkErr(expected: String, block: () -> Unit) {
        val actual = try {
            block()
            "OK"
        } catch (e: Ld_Exception) {
            "LDE:${e.code}"
        } catch (e: DocException) {
            "DOCE:${e.code}"
        }
        assertEquals(expected, actual)
    }

    protected fun chkComment(name: String, exp: String?, block: Ld_NamespaceBodyDsl.() -> Unit) {
        val mod = makeModule("test") {
            imports(Lib_Rell.MODULE.lModule)
            block(this)
        }
        chkComment(mod, name, exp)
    }

    protected fun chkComment(mod: L_Module, name: String, exp: String?) {
        val doc = DocUtils.getDocSymbolByPath(mod, name.split("."))
        checkNotNull(doc) { name }
        assertEquals(exp, doc.comment?.strCode())
    }

    companion object {
        fun chkDoc(mod: L_Module, name: String, expectedHeader: String, expectedCode: String) {
            val path = if (name.isEmpty()) listOf() else name.split(".").toList()
            val doc = DocUtils.getDocSymbolByPath(mod, path)
            checkNotNull(doc) { "Symbol not found: $name" }
            chkDoc(doc, expectedHeader, expectedCode)
        }

        fun chkDoc(actualDoc: DocSymbol, expectedHeader: String, expectedCode: String) {
            val actualHeader = getDocHeaderStr(actualDoc)
            val actualCode = actualDoc.declaration.code.strCode()
            assertEquals(expectedHeader, actualHeader)
            assertEquals(expectedCode, actualCode)
        }

        fun getDocHeaderStr(doc: DocSymbol): String {
            val parts = listOfNotNull(doc.kind.name, doc.symbolName.strCode(), doc.mountName)
            return parts.joinToString("|")
        }

        fun makeNsFun(): C_SpecialLibGlobalFunctionBody {
            return object: C_SpecialLibGlobalFunctionBody() {
                override fun compileCall(
                    ctx: C_ExprContext,
                    name: LazyPosString,
                    args: ImmList<S_Expr>
                ): V_Expr {
                    return C_ExprUtils.errorVExpr(ctx, name.pos)
                }
            }
        }

        fun makeTypeCon(): C_SpecialLibGlobalFunctionBody {
            return object: C_SpecialLibGlobalFunctionBody() {
                override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: ImmList<S_Expr>): V_Expr {
                    args.forEach { it.compile(ctx) }
                    return C_ExprUtils.errorVExpr(ctx, name.pos)
                }
            }
        }

        fun makeTypeFun(): C_SpecialLibMemberFunctionBody {
            return object: C_SpecialLibMemberFunctionBody.Simple() {
                override fun compileCallSimple(
                    ctx: C_ExprContext,
                    callCtx: C_LibFuncCaseCtx,
                    selfType: R_Type,
                    args: ImmList<V_Expr>,
                ): V_SpecialMemberFunctionCall = makeTypeFunCall(ctx)
            }
        }

        internal fun makeTypeFunCall(ctx: C_ExprContext): V_SpecialMemberFunctionCall {
            return object: V_SpecialMemberFunctionCall(ctx, R_CtErrorType) {
                override fun calculator(): R_MemberCalculator = R_MemberCalculator_Error(R_CtErrorType, "Error")
            }
        }

        fun makeNsProp(value: Rt_Value = Rt_IntValue.get(123)): C_NamespaceProperty {
            val rType: R_Type = if (value === Rt_UnitValue) R_UnitType else R_IntegerType
            val rrValue = lazy { rtValueToRRConstant(rType, value) }
            return C_NamespaceProperty_RtValue(rrValue, rType, null)
        }

        fun makeTypeProp(pure: Boolean = false): C_SysFunctionBody {
            return C_SysFunctionBody.simple(pure = pure) { _ -> Rt_UnitValue }
        }
    }
}
