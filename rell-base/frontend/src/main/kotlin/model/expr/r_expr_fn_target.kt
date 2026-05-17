/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.ImmList

/** Pure data — execution dispatch is in runtime/rt_fn_call_dispatch.kt */
abstract class R_FunctionCallTarget

class R_FunctionCallTarget_RegularUserFunction(
    val fn: R_RoutineDefinition,
): R_FunctionCallTarget()

class R_FunctionCallTarget_AbstractUserFunction(
    val baseFn: R_FunctionDefinition,
    val overrideGetter: C_LateGetter<R_FunctionBase>,
): R_FunctionCallTarget()

class R_FunctionCallTarget_NativeUserFunction(
    val fnName: FullName,
    val argTypes: ImmList<R_Type>,
    val resultType: R_Type,
): R_FunctionCallTarget()

class R_FunctionCallTarget_Operation(
    val op: R_OperationDefinition,
): R_FunctionCallTarget()

object R_FunctionCallTarget_FunctionValue: R_FunctionCallTarget()

class R_FunctionCallTarget_SysGlobalFunction(
    /** Opaque R_SysFunction reference — resolved by runtime dispatch. */
    val fn: Any,
    val fullName: Lazy<String>,
): R_FunctionCallTarget()

class R_FunctionCallTarget_SysMemberFunction(
    /** Opaque R_SysFunction reference — resolved by runtime dispatch. */
    val fn: Any,
    val fullName: Lazy<String>,
): R_FunctionCallTarget()
