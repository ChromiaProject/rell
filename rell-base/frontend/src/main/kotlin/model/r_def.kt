/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_CompilerExecutor
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.core.C_DefinitionType
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.lateInit
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSourcePos
import net.postchain.rell.base.utils.doc.DocSymbol

class R_EntityBody(
        val keys: ImmList<Key>,
        val indexes: ImmList<Index>,
        val attributes: ImmMap<Name, R_Attribute>,
)

class R_ExternalEntity(
    val chain: R_ExternalChainRef,
    val metaCheck: Boolean,
)

class R_EntityDefinition(
    executor: C_CompilerExecutor,
    val rDefBase: R_DefinitionBase,
    defType: C_DefinitionType,
    val rName: Name,
    val flags: EntityFlags,
    val sqlMapping: R_EntitySqlMapping,
    val external: R_ExternalEntity?,
): R_Definition(rDefBase) {
    val mountName = sqlMapping.mountName
    val metaName = sqlMapping.metaName

    val type = R_EntityType(this)

    val mirrorStructs = R_MirrorStructs(rDefBase, type, defType.name)

    private val bodyLate = executor.lateInit(C_CompilerPass.MEMBERS, ERROR_BODY)

    val keys: ImmList<Key> get() = bodyLate.get().keys
    val indexes: ImmList<Index> get() = bodyLate.get().indexes
    val attributes: ImmMap<Name, R_Attribute> get() = bodyLate.get().attributes

    val strAttributes: ImmMap<String, R_Attribute> by lazy {
        attributes.mapKeysToImmMap { it.key.str }
    }

    fun setBody(body: R_EntityBody) {
        bodyLate.set(body)
    }

    fun attribute(name: String): R_Attribute {
        val attr = strAttributes[name]
        return checkNotNull(attr) { "Entity '$appLevelName' has no attribute '$name'" }
    }

    override fun getDocMembers0() = strAttributes

    companion object {
        private val ERROR_BODY = R_EntityBody(keys = immListOf(), indexes = immListOf(), attributes = immMapOf())
    }
}

class R_ObjectDefinition(
    base: R_DefinitionBase,
    val rEntity: R_EntityDefinition,
): R_Definition(base) {
    val type = R_ObjectType(this)

    override fun getDocMembers0() = rEntity.docMembers
}

class R_StructFlags(
    val typeFlags: TypeFlags,
    val cyclic: Boolean,
    val infinite: Boolean,
)

class R_Struct(
    val name: String,
    val rDefBase: R_DefinitionBase?,
    val mirrorStructs: R_MirrorStructs?,
) {
    /** Index of the corresponding [net.postchain.rell.base.model.rr.RR_StructDefinition] in [net.postchain.rell.base.model.rr.RR_App.allStructs]. Set during RR resolution. */
    var rrDefIndex: Int = -1
        internal set

    private var bodyLate by LateInit(ERROR_BODY)
    private var flagsLate by LateInit(ERROR_STRUCT_FLAGS)

    val attributes: ImmMap<Name, R_Attribute> get() = bodyLate.attrMap
    val attributesList: ImmList<R_Attribute> get() = bodyLate.attrList
    val flags: R_StructFlags get() = flagsLate

    val strAttributes: ImmMap<String, R_Attribute> by lazy {
        attributes.mapKeysToImmMap { it.key.str }
    }

    val type = R_StructType(this)
    val virtualType = R_VirtualStructType(type)

    fun setAttributes(attrs: ImmMap<Name, R_Attribute>) {
        val attrsList = attrs.values.toImmList()
        attrsList.withIndex().forEach { (idx, attr) -> checkEquals(attr.index, idx) }
        val attrMutable = attrs.values.any { it.mutable }
        bodyLate = R_StructBody(attrs, attrsList, attrMutable)
    }

    fun setFlags(flags: R_StructFlags) {
        flagsLate = flags
    }

    fun isDirectlyMutable() = bodyLate.attrMutable

    override fun toString() = name

    private class R_StructBody(
            val attrMap: ImmMap<Name, R_Attribute>,
            val attrList: ImmList<R_Attribute>,
            val attrMutable: Boolean,
    )

    companion object {
        private val ERROR_BODY = R_StructBody(attrMap = immMapOf(), attrList = immListOf(), attrMutable = false)

        private val ERROR_TYPE_FLAGS = TypeFlags(
            pure = true,
            mutable = false,
            gtv = GtvCompatibility.FULL,
            virtualable = true,
            mixedTuple = false,
            hasTypeVariable = false,
        )

        private val ERROR_STRUCT_FLAGS = R_StructFlags(typeFlags = ERROR_TYPE_FLAGS, cyclic = false, infinite = false)
    }
}

class R_MirrorStructs(
    defBase: R_DefinitionBase,
    val innerType: R_Type,
    val defTypeName: String,
) {
    val immutable = createStruct(defBase, false)
    val mutable = createStruct(defBase, true)

    val operation: R_OperationDefinition? = (innerType as? R_OperationType)?.rOperation

    fun getStruct(mutable: Boolean) = if (mutable) this.mutable else this.immutable

    private fun createStruct(
        defBase: R_DefinitionBase,
        mutable: Boolean,
    ): R_Struct {
        val mutableStr = if (mutable) "mutable " else ""
        val structName = "struct<$mutableStr${defBase.defName.appLevelName}>"
        return R_Struct(structName, defBase, mirrorStructs = this)
    }
}

class R_StructDefinition(
    base: R_DefinitionBase,
    val struct: R_Struct,
): R_Definition(base) {
    val type = struct.type

    val hasDefaultConstructor: Boolean by lazy {
        struct.attributesList.all { it.expr != null }
    }

    override fun getDocMembers0() = struct.strAttributes
}

class R_EnumAttr(
        val rName: Name,
        val value: Int,
        val ideInfo: C_IdeSymbolInfo,
        override val docSourcePos: DocSourcePos?,
): DocDefinition() {
    val name = rName.str

    override val docSymbol: DocSymbol get() = ideInfo.getIdeInfo().doc ?: DocSymbol.NONE

}

class R_EnumDefinition(
    base: R_DefinitionBase,
    val attrs: ImmList<R_EnumAttr>,
): R_Definition(base) {
    val type = R_EnumType(this)

    private val attrMap = attrs.associateByToImmMap { it.name }

    fun attr(name: String): R_EnumAttr? = attrMap[name]

    fun attr(value: Int): R_EnumAttr? = if (value < 0 || value >= attrs.size) null else {
        attrs[value]
    }

    override fun getDocMembers0() = attrMap
}

class R_GlobalConstantBody(val type: R_Type, val expr: R_Expr, val value: RR_ConstantValue?) {
    companion object {
        val ERROR = R_GlobalConstantBody(R_CtErrorType, C_ExprUtils.ERROR_R_EXPR, null)
    }
}

class R_GlobalConstantDefinition(
    base: R_DefinitionBase,
    val constId: GlobalConstantId,
    val bodyGetter: C_LateGetter<R_GlobalConstantBody>,
): R_Definition(base)
