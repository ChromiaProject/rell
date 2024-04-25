/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_AttrHeader
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.utils.Nullable
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.ide.IdeLocalSymbolLink
import net.postchain.rell.base.utils.ide.IdeSymbolCategory
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.base.utils.immListOf

class C_AttrHeader(
        val pos: S_Pos,
        val name: C_Name,
        val type: R_Type?,
        val isExplicitType: Boolean,
        val ideInfo: C_IdeSymbolInfo,
) {
    val rName = name.rName
}

abstract class C_AttrHeaderIdeData {
    abstract fun ideDef(ctx: C_DefinitionContext, pos: S_Pos, attrName: R_Name): C_IdeSymbolDef
}

class C_GlobalAttrHeaderIdeData(
    private val ideCat: IdeSymbolCategory,
    private val ideKind: IdeSymbolKind,
    private val defIdeInfo: C_IdeSymbolInfo?,
    private val docGetter: C_LateGetter<Nullable<DocSymbol>> = C_LateGetter.const(Nullable.of()),
): C_AttrHeaderIdeData() {
    override fun ideDef(ctx: C_DefinitionContext, pos: S_Pos, attrName: R_Name): C_IdeSymbolDef {
        val ideId = C_CommonDefinitionBase.ideId(ctx.definitionType, ctx.defName, ideCat to attrName)
        val ideDef = C_CommonDefinitionBase.ideDef(pos, ideKind, ideId, docGetter)
        return if (defIdeInfo == null) ideDef else C_IdeSymbolDef(defIdeInfo, ideDef.refInfo)
    }
}

class C_LocalAttrHeaderIdeData(
    private val ideKind: IdeSymbolKind,
    private val docGetter: C_LateGetter<Nullable<DocSymbol>>,
): C_AttrHeaderIdeData() {
    override fun ideDef(ctx: C_DefinitionContext, pos: S_Pos, attrName: R_Name): C_IdeSymbolDef {
        val ideLink = IdeLocalSymbolLink(pos)
        return C_IdeSymbolDef.makeLate(ideKind, link = ideLink, docGetter = docGetter)
    }
}

sealed class C_AttrHeaderHandle(val pos: S_Pos, val name: C_Name) {
    val rName = name.rName

    abstract fun compile(ctx: C_DefinitionContext, canInferType: Boolean, ideData: C_AttrHeaderIdeData): C_AttrHeader
}

class C_NamedAttrHeaderHandle(
    private val nameHand: C_NameHandle,
    private val type: R_Type,
): C_AttrHeaderHandle(nameHand.pos, nameHand.name) {
    override fun compile(ctx: C_DefinitionContext, canInferType: Boolean, ideData: C_AttrHeaderIdeData): C_AttrHeader {
        val ideDef = ideData.ideDef(ctx, pos, nameHand.rName)
        nameHand.setIdeInfo(ideDef.defInfo)
        return C_AttrHeader(nameHand.pos, nameHand.name, type, true, ideDef.refInfo)
    }
}

class C_AnonAttrHeaderHandle(
    private val ctx: C_NamespaceContext,
    private val typeNameHand: C_QualifiedNameHandle,
    private val nullable: Boolean,
): C_AttrHeaderHandle(typeNameHand.pos, typeNameHand.last.name) {
    override fun compile(ctx: C_DefinitionContext, canInferType: Boolean, ideData: C_AttrHeaderIdeData): C_AttrHeader {
        val typeName = typeNameHand.cName
        val lastNameHand = typeNameHand.last

        val ideDef = ideData.ideDef(ctx, lastNameHand.pos, lastNameHand.rName)

        val isExplicitType: Boolean
        val attrType: R_Type?
        val defIdeInfo = ideDef.defInfo

        if (typeNameHand.parts.size >= 2 || nullable) {
            isExplicitType = true
            attrType = compileType(defIdeInfo.defId)
        } else if (canInferType) {
            isExplicitType = false
            attrType = null
            lastNameHand.setIdeInfo(defIdeInfo)
        } else {
            isExplicitType = false
            attrType = if (lastNameHand.str == "_") {
                lastNameHand.setIdeInfo(defIdeInfo)
                C_Errors.errAttributeTypeUnknown(ctx.msgCtx, lastNameHand.name)
                R_CtErrorType
            } else {
                compileType(defIdeInfo.defId)
            }
        }

        return C_AttrHeader(typeName.pos, lastNameHand.name, attrType, isExplicitType, ideDef.refInfo)
    }

    private fun compileType(ideDefId: IdeSymbolId?): R_Type {
        val typeDef = ctx.getType(typeNameHand)
        if (ideDefId != null) {
            ctx.symCtx.setDefId(typeNameHand.last.pos, ideDefId)
        }

        val baseType = typeDef?.compileType(ctx.appCtx, typeNameHand.pos, immListOf())

        var type = when {
            baseType == null -> null
            nullable -> C_Types.toNullable(baseType)
            else -> baseType
        }

        if (type != null) {
            type = S_AttrHeader.checkUnitType(ctx.msgCtx, typeNameHand.pos, type, typeNameHand.last.name)
        }

        return type ?: R_CtErrorType
    }
}

class C_SysAttribute(
    val name: R_Name,
    val type: R_Type,
    val mutable: Boolean,
    val isKey: Boolean,
    val expr: R_Expr?,
    val sqlMapping: String,
    val canSetInCreate: Boolean,
    val docSymbol: DocSymbol?,
    val restrictions: C_MemberRestrictions,
) {
    // Name as String, not R_Name.
    constructor(
        name: String,
        type: R_Type,
        mutable: Boolean = false,
        isKey: Boolean = false,
        expr: R_Expr? = null,
        sqlMapping: String = name,
        canSetInCreate: Boolean = true,
        docSymbol: DocSymbol? = null,
        restrictions: C_MemberRestrictions = C_MemberRestrictions.NULL,
    ): this(
        name = R_Name.of(name),
        type = type,
        mutable = mutable,
        isKey = isKey,
        expr = expr,
        sqlMapping = sqlMapping,
        canSetInCreate = canSetInCreate,
        docSymbol = docSymbol,
        restrictions = restrictions,
    )

    fun compile(index: Int, persistent: Boolean): R_Attribute {
        val defaultValue = if (expr == null) null else R_DefaultValue(expr, false)
        val exprGetter = if (defaultValue == null) null else C_LateGetter.const(defaultValue)

        val keyIndexKind = if (isKey) R_KeyIndexKind.KEY else null
        val ideKind = C_AttrUtils.getIdeSymbolKind(persistent, mutable, keyIndexKind)
        val ideInfo = C_IdeSymbolInfo.direct(ideKind, doc = docSymbol)

        return R_Attribute(
            index,
            name,
            type,
            mutable = mutable,
            keyIndexKind = keyIndexKind,
            ideInfo = ideInfo,
            restrictions = restrictions,
            canSetInCreate = canSetInCreate,
            exprGetter = exprGetter,
            sqlMapping = sqlMapping,
        )
    }

    class Maker(
        private val rEntityDefName: R_DefinitionName,
        private val docFactory: DocSymbolFactory,
    ) {
        fun make(
            name: String,
            type: R_Type,
            mutable: Boolean = false,
            isKey: Boolean = false,
            sqlMapping: String = name,
            expr: R_Expr? = null,
            canSetInCreate: Boolean = true,
        ): C_SysAttribute {
            val rName = R_Name.of(name)

            val docDec = DocDeclaration_EntityAttribute(
                rName,
                type = L_TypeUtils.docType(type.mType),
                isMutable = mutable,
                keyIndexKind = if (isKey) R_KeyIndexKind.KEY else null,
            )

            val doc = docFactory.makeDocSymbol(
                DocSymbolKind.ENTITY_ATTR,
                DocSymbolName.global(rEntityDefName.module, "${rEntityDefName.qualifiedName}.$rName"),
                docDec,
            )

            return C_SysAttribute(
                name = name,
                type = type,
                mutable = mutable,
                isKey = isKey,
                sqlMapping = sqlMapping,
                expr = expr,
                canSetInCreate = canSetInCreate,
                docSymbol = doc,
            )
        }
    }
}

class C_CompiledAttribute(
        val defPos: S_Pos?,
        val rAttr: R_Attribute
)

object C_AttrUtils {
    fun getIdeSymbolKind(persistent: Boolean, mutable: Boolean, keyIndexKind: R_KeyIndexKind?): IdeSymbolKind {
        return if (persistent) {
            when (keyIndexKind) {
                null -> if (mutable) IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL_VAR else IdeSymbolKind.MEM_ENTITY_ATTR_NORMAL
                R_KeyIndexKind.KEY -> if (mutable) IdeSymbolKind.MEM_ENTITY_ATTR_KEY_VAR else IdeSymbolKind.MEM_ENTITY_ATTR_KEY
                R_KeyIndexKind.INDEX -> if (mutable) IdeSymbolKind.MEM_ENTITY_ATTR_INDEX_VAR else IdeSymbolKind.MEM_ENTITY_ATTR_INDEX
            }
        } else {
            if (mutable) IdeSymbolKind.MEM_STRUCT_ATTR_VAR else IdeSymbolKind.MEM_STRUCT_ATTR
        }
    }
}
